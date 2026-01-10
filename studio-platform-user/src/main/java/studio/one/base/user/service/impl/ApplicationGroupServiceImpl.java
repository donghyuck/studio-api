package studio.one.base.user.service.impl;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupMembershipId;
import studio.one.base.user.domain.entity.ApplicationGroupRole;
import studio.one.base.user.domain.entity.ApplicationGroupRoleId;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.exception.GroupNotFoundException;
import studio.one.base.user.persistence.ApplicationGroupMembershipRepository;
import studio.one.base.user.persistence.ApplicationGroupRepository;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.base.user.service.ApplicationGroupService;
import studio.one.base.user.service.BatchResult;
import studio.one.platform.component.State;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Service(ApplicationGroupService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ApplicationGroupServiceImpl
        implements ApplicationGroupService<ApplicationGroup, ApplicationRole, ApplicationUser> {

    private final ApplicationGroupRepository groupRepo;
    private final ApplicationUserRepository userRepo;
    private final ApplicationRoleRepository roleRepo;
    private final ApplicationGroupMembershipRepository membershipRepo;
    private final ApplicationGroupRoleRepository groupRoleRepo;
    private final JdbcTemplate jdbcTemplate;

    private final ObjectProvider<I18n> i18nProvider;

    @PostConstruct
    void initialize() {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, "autoconfig.feature.service.details", "User",
                LogUtils.blue(getClass(), true), LogUtils.red(State.INITIALIZED.toString())));
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationGroup getById(Long groupId) {
        return groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
    }

    @Override
    public ApplicationGroup createGroup(ApplicationGroup group) {
        group.setGroupId(null);
        return groupRepo.save(group);
    }

    @Override
    public ApplicationGroup updateGroup(Long groupId, Consumer<ApplicationGroup> mutator) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(mutator, "mutator");
        ApplicationGroup entity = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        mutator.accept(entity);
        return groupRepo.save(entity);
    }

    @Override
    public void deleteGroup(Long groupId) {
        groupRepo.deleteById(groupId);
    }

    // --- 멤버십 ---
    @Override
    public void addMember(Long groupId, Long userId, @Nullable String joinedBy) {
        ApplicationGroup g = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        ApplicationGroupMembershipId id = new ApplicationGroupMembershipId(groupId, userId);
        if (!membershipRepo.existsById(id)) {
            membershipRepo
                    .save(ApplicationGroupMembership.builder().id(id).group(g).joinedBy(joinedBy).build());
        }
    }

    @Override
    public int addMembers(Long groupId, List<Long> userIds, @Nullable String joinedBy) {
        List<Long> candidates = userIds.stream()
                .filter(Objects::nonNull)
                .distinct().toList();
        if (candidates.isEmpty())
            return 0;
        List<Long> exist = membershipRepo.findExistingUserIdsInGroup(groupId, candidates);
        Set<Long> existing = new HashSet<>(exist);
        List<Long> toInsert = candidates.stream()
                .filter(uid -> !existing.contains(uid)).toList();
        if (toInsert.isEmpty())
            return 0;

        List<ApplicationGroupMembership> batch = new ArrayList<>(toInsert.size());
        for (Long uid : toInsert) {
            ApplicationGroupMembership m = new ApplicationGroupMembership();
            m.setId(new ApplicationGroupMembershipId(groupId, uid));
            m.setJoinedBy(joinedBy);
            batch.add(m);
        }
        membershipRepo.saveAll(batch);
        return batch.size();
    }

    @Override
    public int addMembersBulk(Long groupId,
            List<Long> userIds,
            @Nullable String joinedBy,
            @Nullable OffsetDateTime joinedAt) {
        if (groupId == null || userIds == null)
            return 0;

        final Long[] arr = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toArray(Long[]::new);
        if (arr.length == 0)
            return 0;

        final String sql = """
                insert into tb_application_group_members (group_id, user_id, joined_at, joined_by)
                select ?, uid, coalesce(?, now()), ?
                from unnest(?::bigint[]) as uid
                on conflict (group_id, user_id) do nothing
                """;

        final String actor = (joinedBy == null || joinedBy.isEmpty()) ? "system" : joinedBy;

        return jdbcTemplate.update(con -> {
            final PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, groupId);
            ps.setObject(2, joinedAt);
            ps.setString(3, actor);
            // PG 배열 타입: "int8" or "bigint" (둘 중 하나 사용)
            ps.setArray(4, con.createArrayOf("int8", arr));
            return ps;
        });
    }

    @Override
    public void removeMember(Long groupId, Long userId) {
        membershipRepo.deleteById(new ApplicationGroupMembershipId(groupId, userId));
    }

    @Override
    public int removeMembers(Long groupId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty())
            return 0;
        return membershipRepo.deleteByGroupIdAndUserIds(groupId, userIds);
    }

    // --- 롤 ---
    public BatchResult updateGroupRolesBulk(Long groupId, List<Long> desired, String actor) {

        List<Long> current = roleRepo.findRolesByGroupId(groupId).stream().map(Role::getRoleId).filter(Objects::nonNull)
                .toList();
        Set<Long> desiredSet = new HashSet<>(desired);
        Set<Long> currentSet = new HashSet<>(current);

        List<Long> toAssign = desiredSet.stream()
                .filter(id -> !currentSet.contains(id))
                .toList();

        List<Long> toRevoke = currentSet.stream()
                .filter(id -> !desiredSet.contains(id))
                .toList();

        long inserted = toAssign.isEmpty() ? 0 : assignRolesBulk(groupId, toAssign, actor).getInserted();
        long deleted = toRevoke.isEmpty() ? 0 : groupRoleRepo.deleteByGroupIdAndRoleIds(groupId, toRevoke);
        long skipped = toAssign.size() - inserted;
        return new BatchResult(desired.size(), inserted, skipped, deleted);
    }

    public BatchResult assignRolesBulk(Long groupId, List<Long> roles, String actor) {

        List<Long> candidates = (roles == null)
                ? java.util.Collections.emptyList()
                : roles.stream().filter(Objects::nonNull).distinct().toList();
        if (candidates.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        Set<Long> existingRoleIds = new HashSet<>(roleRepo.findExistingIds(candidates));
        List<Long> valid = candidates.stream()
                .filter(existingRoleIds::contains).toList();
        if (valid.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }

        final String sql = """
                insert into tb_application_group_roles (group_id, role_id, assigned_at, assigned_by)
                select ?, uid, now(), ? from unnest(?::bigint[]) as uid
                on conflict (group_id, role_id) do nothing
                """;

        final Long[] arr = valid.toArray(new Long[0]);
        final String assignedBy = (actor != null && !actor.isEmpty()) ? actor : "system";

        int inserted = jdbcTemplate.update(con -> {
            final java.sql.PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, groupId);
            ps.setString(2, assignedBy);
            // PostgreSQL: "int8" 또는 "bigint" 중 하나 사용
            ps.setArray(3, con.createArrayOf("bigint", arr));
            return ps;
        });

        long skipped = valid.size() - inserted;
        return new BatchResult(valid.size(), inserted, skipped, 0);
    }

    public BatchResult assignRoles(Long groupId, List<Long> roles, String actor) {
        List<Long> candidates = roles == null ? List.of()
                : roles.stream().filter(Objects::nonNull).distinct().toList();
        if (candidates.isEmpty())
            return new BatchResult(0, 0, 0, 0);
        int inserted = 0;
        int skipped = 0;
        for (Long rid : candidates) {
            if (groupRoleRepo.existsByGroupIdAndRoleId(groupId, rid)) {
                skipped++;
                continue;
            }
            ApplicationGroupRole gr = new ApplicationGroupRole();
            gr.setId(new ApplicationGroupRoleId(groupId, rid));
            gr.setAssignedBy(actor);
            groupRoleRepo.save(gr);
            inserted++;
        }
        return new BatchResult(candidates.size(), inserted, skipped, 0);
    }

    @Override
    public void assignRole(Long groupId, Long roleId, String by) {
        ApplicationGroup g = groupRepo.findById(groupId).orElseThrow(() -> GroupNotFoundException.byId(groupId));
        ApplicationRole r = roleRepo.findById(roleId).orElseThrow(() -> new NotFoundException("Role", roleId));

        ApplicationGroupRoleId id = new ApplicationGroupRoleId(groupId, roleId);
        if (!groupRoleRepo.existsByGroupIdAndRoleId(groupId, roleId)) {
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
    public Page<ApplicationUser> getMembers(Long groupId, Pageable pageable) {
        return userRepo.findUsersByGroupId(groupId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationRole> getRoles(Long groupId, Pageable pageable) {
        return roleRepo.findRolesByGroupId(groupId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationRole> getRoles(Long groupId) {
        return roleRepo.findRolesByGroupId(groupId, Pageable.unpaged()).getContent();
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
    public Page<ApplicationGroup> getGroups(Pageable pageable) {
        return groupRepo.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationGroup> getGroupsWithMemberCount(Pageable pageable) {
        return getGroupsWithMemberCount(null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApplicationGroup> getGroupsWithMemberCount(String q, Pageable pageable) {
        return groupRepo.findGroupsWithMemberCountByName(q, pageable)
                .map(p -> {
                    ApplicationGroup g = p.getEntity();
                    g.setMemberCount(p.getMemberCount() == null ? 0L : p.getMemberCount().longValue());
                    return g;
                });
    }

}
