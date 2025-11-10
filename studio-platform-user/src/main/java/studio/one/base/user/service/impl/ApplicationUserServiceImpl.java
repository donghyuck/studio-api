package studio.one.base.user.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils; 
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.entity.ApplicationUserRole;
import studio.one.base.user.domain.entity.ApplicationUserRoleId;
import studio.one.base.user.domain.event.UserDisabledEvent;
import studio.one.base.user.domain.event.UserEnabledEvent;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.UserIdOnly;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;
import studio.one.base.user.persistence.ApplicationGroupRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.base.user.persistence.ApplicationUserRoleRepository;
import studio.one.base.user.exception.GroupNotFoundException;
import studio.one.base.user.exception.RoleNotFoundException;
import studio.one.base.user.exception.UserAlreadyExistsException;
import studio.one.base.user.exception.UserNotFoundException;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.service.BatchResult;
import studio.one.platform.service.DomainEvents;

@Service(ApplicationUserService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ApplicationUserServiceImpl implements ApplicationUserService<ApplicationUser, ApplicationRole> {

    private final ApplicationUserRepository userRepo;
    private final ApplicationRoleRepository roleRepo;
    private final ApplicationGroupRepository groupRepo;
    private final ApplicationUserRoleRepository userRoleRepo;
    private final ApplicationGroupMembershipRepository membershipRepo;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DomainEvents domainEvents;
    private final Clock clock;
    

    // ---------- 기본 CRUD ----------
    @Transactional(propagation = Propagation.SUPPORTS)
    public Page<ApplicationUser> findAll(Pageable pageable) {
        return userRepo.findAll(pageable);
    }
 
    public Page<ApplicationUser> findByNameOrUsernameOrEmail(String keyword, Pageable pageable) {
         return userRepo.search(keyword, pageable);
    }

    @Cacheable(cacheNames = "users.byUserId", key = "#userId", unless = "#result == null")
    @Transactional(propagation = Propagation.SUPPORTS)
    public ApplicationUser get(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));
    }

    @Cacheable(cacheNames = "users.byUsername", key = "#username", unless = "#result == null")
    @Transactional(propagation = Propagation.SUPPORTS)
    public Optional<ApplicationUser> findByUsername(String username) {
        return userRepo.findByUsername(username);
    }


    @Transactional
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

    @Caching(
        evict = {
            @CacheEvict (cacheNames = "users.byUsername, users.byUserId", allEntries = true ) 
        }
    )
    @Transactional
    public ApplicationUser update(Long userId, Consumer<ApplicationUser> mutator) {
        Objects.requireNonNull(mutator, "updater");
        ApplicationUser u = userRepo.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
        mutator.accept(u);
        encodePasswordIfPresent(u);
        log.debug("[UserUpdate] username={}, failedAttempts={}, lastFailedAt={}, lockedUntil={}", u.getUsername(), u.getFailedAttempts(), u.getLastFailedAt(), u.getAccountLockedUntil());
        ApplicationUser saved = userRepo.save(u);
        return saved;
    }

    @Caching(
        evict = {
            @CacheEvict (cacheNames = "users.byUsername, users.byUserId, users.userId.byUsername", allEntries = true ) 
        }
    )
    @Transactional
    public void delete(Long userId) {
        ApplicationUser u = get(userId);
        userRepo.delete(u);
    }

    @Override
    @Transactional
    @Caching(
        evict = {
            @CacheEvict (cacheNames = "users.byUsername,users.byUserId", allEntries = true ) 
        }
    )
    public void enable(Long userId, String actor) {
        ApplicationUser u = get(userId);
        if (!Boolean.TRUE.equals(u.isEnabled())) {
            u.setEnabled(true);
            safeAudit("USER_ENABLED", userId, actor, null);
            domainEvents.publishAfterCommit(UserEnabledEvent.of(userId, actor, clock));
        }
    }

    void safeAudit(String action, Long userId, String actor, String reason) {
        log.info("Audit - Action: {}, UserID: {}, Actor: {}, Reason: {}", action, userId, actor, reason);
    }

    @Override
    @Transactional
    @Caching(
        evict = {
            @CacheEvict (cacheNames = "users.byUsername,users.byUserId", allEntries = true ) 
        }
    )
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

    
    // ---------- 그룹 가입/탈퇴 ----------
    @Caching(
        evict = {
            @CacheEvict (cacheNames = "roles.effective", key = "userId" ) 
        }
    )
    @Transactional
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

    @Caching(
        evict = {
            @CacheEvict (cacheNames = "roles.effective", key = "userId" ) 
        }
    )
    @Transactional
    public void leaveGroup(Long userId, Long groupId) {
        membershipRepo.deleteById(new ApplicationGroupMembershipId(groupId, userId));
    }

    public List<ApplicationRole> findRolesByUser(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    public List<ApplicationGroup> findGroupsByUser(Long userId) {
        return userRepo.findGroupsByUserId(userId);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public Set<ApplicationRole> findEffectiveRolesFast(Long userId) {
        return new LinkedHashSet<>(roleRepo.findEffectiveRolesByUserId(userId));
    }

    @Cacheable(cacheNames = "roles.effective", key = "#userId")
    @Transactional(propagation = Propagation.SUPPORTS)
    public Set<ApplicationRole> findEffectiveRoles(Long userId) {
        log.debug("find effective roles for user {}", userId);
        List<ApplicationRole> direct = roleRepo.findRolesByUserId(userId);
        log.debug("found role directly to user : {}", direct);
        List<Long> groupIds = userRepo.findGroupIdsByUserId(userId);
        log.debug("found group ids of user : {}", groupIds);
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

    @Override
    public List<ApplicationRole> getUserRoles(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    @Override
    public List<ApplicationRole> getUserGroupsRoles(Long userId) {
        List<Long> groupIds = userRepo.findGroupIdsByUserId(userId);
        List<ApplicationRole> viaGroups = groupIds.isEmpty()
                ? List.of()
                : roleRepo.findRolesByGroupIds(groupIds);
        return viaGroups;
    }


    // ---------- 롤 부여/회수 ----------

    @Caching(
        evict = {
            @CacheEvict (cacheNames = "roles.effective", key = "userId" ) 
        }
    )    
    @Transactional
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
    @Caching(
        evict = {
            @CacheEvict (cacheNames = "roles.effective", key = "userId" ) 
        }
    )
    @Transactional
    public void revokeRole(Long userId, Long roleId) {
        userRoleRepo.deleteByUserIdAndRoleId(userId, roleId);
    }

    @Override
    @Transactional
    public BatchResult updateUserRolesBulk(Long userId, List<Long> desired, String actor) {
        List<Long> current =  roleRepo.findRolesByUserId(userId).stream().map(Role::getRoleId).filter(Objects::nonNull).collect(Collectors.toList());
        Set<Long> desiredSet = new HashSet<>(desired);
        Set<Long> currentSet = new HashSet<>(current);
        List<Long> toAssign = desiredSet.stream()
        .filter(id -> !currentSet.contains(id))
        .toList();
        List<Long> toRevoke = currentSet.stream()
        .filter(id -> !desiredSet.contains(id))
        .toList();
        long inserted = toAssign.isEmpty() ? 0 : assignRolesBulk(userId, toAssign, actor).getInserted();
        long deleted  = toRevoke.isEmpty() ? 0 : userRoleRepo.deleteByUserIdAndRoleIds(userId, toRevoke);
        long skipped = toAssign.size() - inserted;
        return new BatchResult(desired.size(), inserted, skipped, deleted);
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public BatchResult assignRolesBulk(Long userId, List<Long> roles, String actor) {

        List<Long> candidates = (roles == null)
                ? java.util.Collections.emptyList()
                : roles.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        Set<Long> existingRoleIds = new HashSet<>(roleRepo.findExistingIds(candidates));
        List<Long> valid = candidates.stream()
                .filter(existingRoleIds::contains)
                .toList();
        if (valid.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        final String sql = "insert into tb_application_user_roles (user_id, role_id, assigned_at, assigned_by) " +
                "select ?, uid, now(), ? from unnest(?::bigint[]) as uid " +
                "on conflict (user_id, role_id) do nothing";

        final Long[] arr = valid.toArray(new Long[0]);
        final String assignedBy = (actor != null && !actor.isEmpty()) ? actor : "system";

        int inserted = jdbcTemplate.update(con -> {
            final java.sql.PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, userId);
            ps.setString(2, assignedBy); 
            ps.setArray(3, con.createArrayOf("bigint", arr));
            return ps;
        });

        long skipped = valid.size() - inserted;
        return new BatchResult(valid.size(), inserted, skipped, 0);
    }
 
    @Cacheable(cacheNames = "users.userId.byUsername", key = "#username", unless = "#result == null")
    @Transactional(propagation = Propagation.SUPPORTS)
    public Long findIdByUsername(String username) {
        if (!StringUtils.isEmpty(username))
            throw new IllegalArgumentException("Username is empty");
        return userRepo.findFirstByUsernameIgnoreCase(username)
            .map(UserIdOnly::getUserId)
            .orElseThrow(() -> UserNotFoundException.of(username));
    }
    
}