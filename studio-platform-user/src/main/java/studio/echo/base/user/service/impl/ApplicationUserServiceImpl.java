package studio.echo.base.user.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.domain.entity.ApplicationGroupMembership;
import studio.echo.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.entity.ApplicationUserRole;
import studio.echo.base.user.domain.entity.ApplicationUserRoleId;
import studio.echo.base.user.domain.event.UserDisabledEvent;
import studio.echo.base.user.domain.event.UserEnabledEvent;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.repository.ApplicationGroupMembershipRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationUserRepository;
import studio.echo.base.user.domain.repository.ApplicationUserRoleRepository;
import studio.echo.base.user.exception.GroupNotFoundException;
import studio.echo.base.user.exception.RoleNotFoundException;
import studio.echo.base.user.exception.UserAlreadyExistsException;
import studio.echo.base.user.exception.UserNotFoundException;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.platform.service.DomainEvents;

@Service(ApplicationUserService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ApplicationUserServiceImpl implements ApplicationUserService<ApplicationUser> {

    private final ApplicationUserRepository userRepo;
    private final ApplicationRoleRepository roleRepo;
    private final ApplicationGroupRepository groupRepo;
    private final ApplicationUserRoleRepository userRoleRepo;
    private final ApplicationGroupMembershipRepository membershipRepo;
    private final ApplicationGroupRoleRepository groupRoleRepo;
    private final PasswordEncoder passwordEncoder;
    private final DomainEvents domainEvents;
    private final Clock clock;

    // ---------- 기본 CRUD ----------
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationUser> findAll(Pageable pageable) {
        return userRepo.findAll(pageable);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public ApplicationUser get(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<ApplicationUser> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    public ApplicationUser create(ApplicationUser user) {
        user.setUserId(null);
        final String username = user.getUsername();
        if (userRepo.existsByUsername(username))
            throw UserAlreadyExistsException.byName(user.getUsername());
        encodePasswordIfPresent(user); 
        if (user.isEnabled())
            user.setEnabled(Boolean.TRUE);
        ApplicationUser saved = userRepo.save(user);
        log.debug("User created: id={}, username={}", saved.getUserId(), saved.getUsername());
        return saved;
    }

    public ApplicationUser update(Long userId, Consumer<ApplicationUser> mutator) {
        ApplicationUser u = get(userId);
        mutator.accept(u);
        encodePasswordIfPresent(u);
        ApplicationUser saved = userRepo.save(u);
        return saved;
    }

    public void delete(Long userId) {
        ApplicationUser u = get(userId);
        userRepo.delete(u);
    }

    @Override
    public void enable(Long userId, String actor) {
        ApplicationUser u = get(userId);
        if (!Boolean.TRUE.equals(u.isEnabled())) {
            u.setEnabled(true);
            safeAudit("USER_ENABLED", userId, actor, null);
            domainEvents.publishAfterCommit(UserEnabledEvent.of(userId, actor, clock));
        }
    }

    @Override
    public void disable(Long userId, String actor, String reason, OffsetDateTime until, boolean revokeTokens,
            boolean invalidateSessions, boolean notifyUser) {

        ApplicationUser u = get(userId);
        if (!Boolean.FALSE.equals(u.isEnabled())) {
            u.setEnabled(false);
            safeAudit("USER_DISABLED", userId, actor, reason);
            domainEvents.publishAfterCommit(UserDisabledEvent.of(userId, actor, reason, until, clock));
        }
    }

    public Page<ApplicationUser> search(String q, Pageable pageable) {
        return userRepo.search(q, pageable);
    }

    public Page<ApplicationUser> getUsersByGroup(Long groupId, Pageable pageable) {
        return userRepo.findUsersByGroupId(groupId, pageable);
    }

    public List<ApplicationUser> getUsersByGroup(Long groupId) {
        return userRepo.findUsersByGroupId(groupId);
    }

    // ---------- 롤 부여/회수 ----------
    public void assignRole(Long userId, Long roleId, String by) {
        ApplicationUser u = userRepo.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
        ApplicationRole r = roleRepo.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.byId(roleId));
        ApplicationUserRoleId id = new ApplicationUserRoleId(u.getUserId(), r.getRoleId());
        if (!userRoleRepo.existsById(id)) {
            userRoleRepo.save(ApplicationUserRole.builder()
                    .id(id)
                    .user(u)
                    .role(r)
                    .assignedBy(by)
                    .build());
        }
    }

    public void revokeRole(Long userId, Long roleId) {
        userRoleRepo.deleteByUserIdAndRoleId(userId, roleId);
    }

    // ---------- 그룹 가입/탈퇴 ----------
    public void joinGroup(Long userId, Long groupId, String by) {
        ApplicationUser u = userRepo.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
        ApplicationGroup g = groupRepo.findById(groupId)
                .orElseThrow(() -> GroupNotFoundException.byId(groupId));

        ApplicationGroupMembershipId id = new ApplicationGroupMembershipId(groupId, userId);
        if (!membershipRepo.existsById(id)) {
            membershipRepo.save(ApplicationGroupMembership.builder()
                    .id(id)
                    .group(g)
                    .user(u)
                    .joinedBy(by)
                    .build());
        }
    }

    public void leaveGroup(Long userId, Long groupId) {
        membershipRepo.deleteById(new ApplicationGroupMembershipId(groupId, userId));
    }

    public List<ApplicationRole> findRolesByUser(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    public List<ApplicationGroup> findGroupsByUser(Long userId) {
        return userRepo.findGroupsByUserId(userId);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Set<ApplicationRole> findEffectiveRolesFast(Long userId) {
        return new LinkedHashSet<>(roleRepo.findEffectiveRolesByUserId(userId));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Set<Role> findEffectiveRoles(Long userId) {
        List<ApplicationRole> direct = roleRepo.findRolesByUserId(userId);
        List<Long> groupIds = userRepo.findGroupIdsByUserId(userId);
        List<ApplicationRole> viaGroups = groupIds.isEmpty()
                ? List.of()
                : roleRepo.findRolesByGroupIds(groupIds);

        return Stream.concat(direct.stream(), viaGroups.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void encodePasswordIfPresent(ApplicationUser user) {
        // 프로젝트 규약에 따라 둘 중 하나 사용:
        // (A) 해시 저장 필드가 password 인 경우
        if (StringUtils.isNotBlank(user.getPassword())) {
            // 이미 해시인지 구분하려면 패턴 검사 추가 가능({bcrypt} or $2a$ ...)
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        // (B) 해시 저장 필드가 passwordHash, 입력 원문이 rawPassword 인 경우
        if (StringUtils.isNotBlank(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
    }

    private void safeAudit(String type, Long subjectId, String actor, String reason) {
        
    }

}