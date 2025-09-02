package studio.echo.base.user.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.echo.base.user.domain.entity.ApplicationGroup;
import studio.echo.base.user.domain.entity.ApplicationGroupMembership;
import studio.echo.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.echo.base.user.domain.entity.ApplicationGroupRole;
import studio.echo.base.user.domain.entity.ApplicationGroupRoleId;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.repository.ApplicationGroupMembershipRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRepository;
import studio.echo.base.user.domain.repository.ApplicationGroupRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationRoleRepository;
import studio.echo.base.user.domain.repository.ApplicationUserRepository;
import studio.echo.base.user.exception.GroupNotFoundException;
import studio.echo.base.user.exception.UserNotFoundException;
import studio.echo.base.user.service.ApplicationGroupService;
import studio.echo.platform.exception.NotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationGroupServiceImpl implements ApplicationGroupService {

    private final ApplicationGroupRepository groupRepo;
    private final ApplicationUserRepository userRepo;
    private final ApplicationRoleRepository roleRepo;
    private final ApplicationGroupMembershipRepository membershipRepo;
    private final ApplicationGroupRoleRepository groupRoleRepo;

    @Override
    @Transactional(readOnly = true)
    public ApplicationGroup get(Long groupId) {
        return groupRepo.findById(groupId)
                .orElseThrow(() -> GroupNotFoundException.byId(groupId));
    }

    @Override
    public ApplicationGroup create(ApplicationGroup group) {
        group.setGroupId(null);
        return groupRepo.save(group);
    }

    @Override
    public ApplicationGroup update(Long groupId, Consumer<ApplicationGroup> mutator) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(mutator, "mutator");
        ApplicationGroup entity = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        mutator.accept(entity);
        return groupRepo.save(entity);
    }

    @Override
    public void delete(Long groupId) {
        groupRepo.deleteById(groupId);
    }

    // --- 멤버십 ---
    @Override
    public void addMember(Long groupId, Long userId, String by) {
        ApplicationGroup g = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        ApplicationUser u = userRepo.findById(userId).orElseThrow(() -> UserNotFoundException.byId(userId));
        ApplicationGroupMembershipId id = new ApplicationGroupMembershipId(groupId, userId);
        if (!membershipRepo.existsById(id)) {
            membershipRepo.save(ApplicationGroupMembership.builder().id(id).group(g).user(u).joinedBy(by).build());
        }
    }

    @Override
    public void removeMember(Long groupId, Long userId) {
        membershipRepo.deleteById(new ApplicationGroupMembershipId(groupId, userId));
    }

    // --- 그룹 롤 ---
    @Override
    public void assignRole(Long groupId, Long roleId, String by) {
        ApplicationGroup g = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        ApplicationRole r = roleRepo.findById(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));

        ApplicationGroupRoleId id = new ApplicationGroupRoleId(groupId, roleId);
        if (!groupRoleRepo.existsByGroup_GroupIdAndRole_RoleId(groupId, roleId)) {
            groupRoleRepo.save(ApplicationGroupRole.builder()
                    .id(id).group(g).role(r).assignedBy(by).build());
        }
    }

    @Override
    public void revokeRole(Long groupId, Long roleId) {
        groupRoleRepo.deleteById(new ApplicationGroupRoleId(groupId, roleId));
    }

    // --- 조회 ---
    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationUser> listMembers(Long groupId, Pageable pageable) {
        return userRepo.findUsersByGroupId(groupId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ApplicationUser> listMembers(Long groupId) {
        return userRepo.findUsersByGroupId(groupId);

    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationRole> listRoles(Long groupId, Pageable pageable) {
        return roleRepo.findRolesByGroupId(groupId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ApplicationRole> listRoles(Long groupId) {
        return groupRoleRepo.findRolesByGroupId(groupId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationGroup> getGroupsByUser(Long userId, Pageable pageable) {
        return groupRepo.findGroupsByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ApplicationGroup> getGroupsByUser(Long userId) {
        return groupRepo.findGroupsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationGroup> findAll(Pageable pageable) {
        return groupRepo.findAll(pageable);
    }
}