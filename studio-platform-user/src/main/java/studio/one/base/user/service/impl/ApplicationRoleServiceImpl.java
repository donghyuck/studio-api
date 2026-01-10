package studio.one.base.user.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.exception.RoleNotFoundException;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;
import studio.one.base.user.persistence.ApplicationRoleRepository;
import studio.one.base.user.persistence.ApplicationUserRoleRepository;
import studio.one.base.user.service.ApplicationRoleService;
import studio.one.base.user.service.BatchResult;
import studio.one.platform.component.State;
import studio.one.platform.security.event.RoleUpdatedEvent;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;
import studio.one.platform.util.I18nUtils;
import studio.one.platform.util.LogUtils;

@Service(ApplicationRoleService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ApplicationRoleServiceImpl
        implements ApplicationRoleService<ApplicationRole, ApplicationGroup, ApplicationUser> {

    private final ApplicationRoleRepository roleRepo;
    private final ApplicationGroupRoleRepository groupRoleRepo;
    private final ApplicationUserRoleRepository userRoleRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<DomainEvents> domainEventsProvider; 
    private final ObjectProvider<I18n> i18nProvider;

    @PostConstruct
    void initialize() {
        I18n i18n = I18nUtils.resolve(i18nProvider);
        log.info(LogUtils.format(i18n, "autoconfig.feature.service.details", "User",
                LogUtils.blue(getClass(), true), LogUtils.red(State.INITIALIZED.toString())));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> getRoles(Pageable pageable) {
        return roleRepo.findAll(pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> search(@Nullable String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return roleRepo.findAll(pageable);
        }
        return roleRepo.search(q, pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ApplicationRole> getRoles() {
        return roleRepo.findAll(Sort.by("name").ascending());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public ApplicationRole getRoleById(Long roleId) {
        return roleRepo.findById(roleId)
                .orElseThrow(() -> RoleNotFoundException.byId(roleId));
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<ApplicationRole> findRoleByName(String name) {
        return roleRepo.findByName(name);
    }

    @Override
    public ApplicationRole createRole(ApplicationRole role) {
        if (!role.getName().startsWith("ROLE_"))
            throw new IllegalArgumentException("Role name must start with 'ROLE_'.");
        ApplicationRole saved = roleRepo.save(role);
        publishEvent(RoleUpdatedEvent.Action.CREATED, saved.getName(), null);
        return saved;
    }

    @Override
    public ApplicationRole updateRole(Long roleId, Consumer<ApplicationRole> mutator) {
        ApplicationRole r = getRoleById(roleId);
        String previousName = r.getName();
        mutator.accept(r);
        if (!r.getName().startsWith("ROLE_"))
            throw new IllegalArgumentException("Role name must start with 'ROLE_'.");
        ApplicationRole saved = roleRepo.save(r);
        publishEvent(RoleUpdatedEvent.Action.UPDATED, saved.getName(), previousName);
        return saved;
    }

    @Override
    public void deleteRole(Long roleId) {
        ApplicationRole existing = getRoleById(roleId);
        String roleName = existing.getName();
        roleRepo.delete(existing);
        publishEvent(RoleUpdatedEvent.Action.DELETED, roleName, null);
    }

    // --- 사용자/그룹 기준 롤 조회 ---
    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> getRolesByUser(Long userId, Pageable pageable) {
        return roleRepo.findRolesByUserId(userId, pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ApplicationRole> getRolesByUser(Long userId) {
        return roleRepo.findRolesByUserId(userId);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public Page<ApplicationRole> getRolesByGroup(Long groupId, Pageable pageable) {
        return roleRepo.findRolesByGroupId(groupId, pageable);
    }

    @Override
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<ApplicationRole> getRolesByGroup(Long groupId) {
        return roleRepo.findRolesByGroupIds(List.of(groupId)); // 혹은 groupRoleRepo로 List 버전 호출
    }

    @Override
    public Page<ApplicationGroup> findGroupsGrantedRole(Long roleId, String q, Pageable pageable) {
        Page<ApplicationGroupWithMemberCount> page = groupRoleRepo.findGroupsWithMemberCountByRoleId(roleId, q,
                pageable);
        return page.map(p -> {
            ApplicationGroup g = p.getEntity();
            g.setMemberCount(p.getMemberCount() == null ? 0L : p.getMemberCount());
            return g;
        });
    }

    @Override
    public Page<ApplicationUser> findUsersGrantedRole(Long roleId, String scope, String q, Pageable pageable) {
        final String s = scope == null ? "effective" : scope.toLowerCase();
        final String keyword = blankToNull(q);
        return switch (s) {
            case "direct" -> userRoleRepo.findUsersByRoleId(roleId, keyword, pageable);
            case "group" -> userRoleRepo.findUsersByRoleIdViaGroup(roleId, keyword, pageable);
            default -> userRoleRepo.findUsersByRoleId(roleId, keyword, pageable); // 간단 구현
        };
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private void publishEvent(RoleUpdatedEvent.Action action, String roleName, String previousName) {
        if (roleName == null || roleName.isBlank())
            return;
        log.debug("{} -> {} : {}.", previousName, roleName, action.toString());    
        domainEventsProvider.ifAvailable(
                resolved -> resolved.publishAfterCommit( RoleUpdatedEvent.of(action, roleName.trim(), previousName))); 
    }

    @Override
    public BatchResult revokeRoleFromGroups(List<Long> groupIds, Long roleId) {
        if (groupIds.isEmpty())
            return new BatchResult(0, 0, 0, 0);
        int deleted = groupRoleRepo.deleteByGroupIdsAndRoleId(groupIds, roleId);
        return new BatchResult(groupIds.size(), 0, 0, deleted);
    }

    @Override
    public BatchResult revokeRoleFromUsers(List<Long> userIds, Long roleId) {
        if (userIds.isEmpty())
            return new BatchResult(0, 0, 0, 0);
        int deleted = userRoleRepo.deleteByUserIdsAndRoleId(userIds, roleId);
        return new BatchResult(userIds.size(), 0, 0, deleted);
    }

    @Override
    public BatchResult assignRoleToUsers(List<Long> userIds, Long roleId, @Nullable String assignedBy,
            @Nullable OffsetDateTime assignedAt) {
        if (roleId == null || userIds == null || userIds.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }
        final List<Long> candidates = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return new BatchResult(0, 0, 0, 0);
        }
        final Long[] arr = candidates.toArray(new Long[0]);

        final String sql = """
                insert into tb_application_user_roles (user_id, role_id, assigned_at, assigned_by)
                select uid, ?, coalesce(?, now()), ?
                from unnest(?::bigint[]) as uid
                on conflict (user_id, role_id) do nothing
                """;
        int inserted = jdbcTemplate.update(con -> {
            final java.sql.PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, roleId);
            ps.setObject(2, assignedAt); // null이면 DB now() 사용
            ps.setString(3, Optional.ofNullable(assignedBy).orElse("system"));
            ps.setArray(4, con.createArrayOf("bigint", arr));
            return ps;
        });

        long requested = candidates.size();
        long skipped = requested - inserted; // 이미 있던 매핑 등으로 인해 삽입 안 된 건수
        return new BatchResult((int) requested, inserted, (int) skipped, 0);
    }

}
