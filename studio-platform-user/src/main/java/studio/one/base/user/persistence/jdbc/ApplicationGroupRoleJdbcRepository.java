package studio.one.base.user.persistence.jdbc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupRole;
import studio.one.base.user.domain.entity.ApplicationGroupRoleId;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.persistence.ApplicationGroupRoleRepository;

@Repository
public class ApplicationGroupRoleJdbcRepository extends BaseJdbcRepository implements ApplicationGroupRoleRepository {

    private static final Map<String, String> GROUP_SORT_COLUMNS = Map.of(
            "groupId", "g.GROUP_ID",
            "name", "g.NAME",
            "description", "g.DESCRIPTION",
            "creationDate", "g.CREATION_DATE",
            "modifiedDate", "g.MODIFIED_DATE");

    private static final RowMapper<ApplicationRole> ROLE_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationRole role = new ApplicationRole();
        role.setRoleId(rs.getLong("ROLE_ID"));
        role.setName(rs.getString("NAME"));
        role.setDescription(rs.getString("DESCRIPTION"));
        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        role.setCreationDate(created == null ? null : created.toInstant());
        role.setModifiedDate(modified == null ? null : modified.toInstant());
        return role;
    };

    private static final RowMapper<ApplicationGroup> GROUP_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationGroup group = new ApplicationGroup();
        group.setGroupId(rs.getLong("GROUP_ID"));
        group.setName(rs.getString("NAME"));
        group.setDescription(rs.getString("DESCRIPTION"));
        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        group.setCreationDate(created == null ? null : created.toInstant());
        group.setModifiedDate(modified == null ? null : modified.toInstant());
        group.setMemberCount(0L);
        group.setProperties(new HashMap<>());
        return group;
    };

    private static final RowMapper<ApplicationGroupWithMemberCount> GROUP_WITH_COUNT = (rs, rowNum) -> {
        ApplicationGroup group = GROUP_ROW_MAPPER.mapRow(rs, rowNum);
        long count = rs.getLong("MEMBER_COUNT");
        group.setMemberCount(rs.wasNull() ? 0L : count);
        return new JdbcGroupWithMemberCount(group, group.getMemberCount());
    };

    public ApplicationGroupRoleJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public List<ApplicationRole> findRolesByGroupId(Long groupId) {
        String sql = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_GROUP_ROLES gr on gr.ROLE_ID = r.ROLE_ID
                 where gr.GROUP_ID = :groupId
                """;
        return namedTemplate.query(sql, Map.of("groupId", groupId), ROLE_ROW_MAPPER);
    }

    @Override
    public List<ApplicationGroup> findGroupsByRoleId(Long roleId) {
        String sql = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = g.GROUP_ID
                 where gr.ROLE_ID = :roleId
                """;
        List<ApplicationGroup> groups = namedTemplate.query(sql, Map.of("roleId", roleId), GROUP_ROW_MAPPER);
        loadGroupProperties(groups);
        return groups;
    }

    @Override
    public boolean existsByGroupIdAndRoleId(Long groupId, Long roleId) {
        String sql = """
                select exists(
                    select 1 from TB_APPLICATION_GROUP_ROLES
                     where GROUP_ID = :groupId
                       and ROLE_ID = :roleId)
                """;
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("groupId", groupId, "roleId", roleId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void deleteByGroupId(Long groupId) {
        namedTemplate.update("delete from TB_APPLICATION_GROUP_ROLES where GROUP_ID = :groupId", Map.of("groupId", groupId));
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        namedTemplate.update("delete from TB_APPLICATION_GROUP_ROLES where ROLE_ID = :roleId", Map.of("roleId", roleId));
    }

    @Override
    public int deleteByGroupIdAndRoleIds(Long groupId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0;
        }
        String sql = """
                delete from TB_APPLICATION_GROUP_ROLES
                 where GROUP_ID = :groupId
                   and ROLE_ID in (:roleIds)
                """;
        return namedTemplate.update(sql, Map.of("groupId", groupId, "roleIds", roleIds));
    }

    @Override
    public int deleteByGroupIdsAndRoleId(Collection<Long> groupIds, Long roleId) {
        if (groupIds == null || groupIds.isEmpty()) {
            return 0;
        }
        String sql = """
                delete from TB_APPLICATION_GROUP_ROLES
                 where GROUP_ID in (:groupIds)
                   and ROLE_ID = :roleId
                """;
        return namedTemplate.update(sql, Map.of("groupIds", groupIds, "roleId", roleId));
    }

    @Override
    public Page<ApplicationGroup> findGroupsByRoleId(Long roleId, String keyword, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("roleId", roleId);
        params.put("q", normalize(keyword));

        String select = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = g.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(g.NAME) like :q or lower(g.DESCRIPTION) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = g.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(g.NAME) like :q or lower(g.DESCRIPTION) like :q)
                """;
        Page<ApplicationGroup> page = queryPage(select, count, params, pageable, GROUP_ROW_MAPPER, "g.GROUP_ID", GROUP_SORT_COLUMNS);
        loadGroupProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByRoleId(Long roleId, String keyword, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("roleId", roleId);
        params.put("q", normalize(keyword));

        String select = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE,
                       (select count(m.USER_ID) from TB_APPLICATION_GROUP_MEMBERS m where m.GROUP_ID = g.GROUP_ID) as MEMBER_COUNT
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = g.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(g.NAME) like :q or lower(g.DESCRIPTION) like :q)
                """;
        String count = """
                select count(distinct g.GROUP_ID)
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = g.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(g.NAME) like :q or lower(g.DESCRIPTION) like :q)
                """;
        Page<ApplicationGroupWithMemberCount> page = queryPage(select, count, params, pageable, GROUP_WITH_COUNT, "g.GROUP_ID", GROUP_SORT_COLUMNS);
        List<ApplicationGroup> entities = page.getContent().stream()
                .map(ApplicationGroupWithMemberCount::getEntity)
                .filter(Objects::nonNull)
                .toList();
        loadGroupProperties(entities);
        return page;
    }

    @Override
    public ApplicationGroupRole save(ApplicationGroupRole groupRole) {
        ApplicationGroupRoleId id = groupRole.getId();
        if (id == null) {
            id = new ApplicationGroupRoleId(
                    groupRole.getGroup() != null ? groupRole.getGroup().getGroupId() : null,
                    groupRole.getRole() != null ? groupRole.getRole().getRoleId() : null);
            groupRole.setId(id);
        }
        LocalDateTime assignedAt = groupRole.getAssignedAt();
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
            groupRole.setAssignedAt(assignedAt);
        }
        String sql = """
                insert into TB_APPLICATION_GROUP_ROLES (GROUP_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY)
                values (:groupId, :roleId, :assignedAt, :assignedBy)
                on conflict (GROUP_ID, ROLE_ID) do update
                      set ASSIGNED_AT = excluded.ASSIGNED_AT,
                          ASSIGNED_BY = excluded.ASSIGNED_BY
                """;
        namedTemplate.update(sql, Map.of(
                "groupId", id.getGroupId(),
                "roleId", id.getRoleId(),
                "assignedAt", Timestamp.valueOf(assignedAt),
                "assignedBy", groupRole.getAssignedBy()));
        return groupRole;
    }

    @Override
    public <S extends ApplicationGroupRole> List<S> saveAll(Iterable<S> groupRoles) {
        List<S> buffer = new ArrayList<>();
        groupRoles.forEach(buffer::add);
        if (buffer.isEmpty()) {
            return buffer;
        }
        String sql = """
                insert into TB_APPLICATION_GROUP_ROLES (GROUP_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY)
                values (:groupId, :roleId, :assignedAt, :assignedBy)
                on conflict (GROUP_ID, ROLE_ID) do update
                      set ASSIGNED_AT = excluded.ASSIGNED_AT,
                          ASSIGNED_BY = excluded.ASSIGNED_BY
                """;
        SqlParameterSource[] batch = buffer.stream()
                .map(gr -> {
                    ApplicationGroupRoleId id = gr.getId();
                    if (id == null) {
                        id = new ApplicationGroupRoleId(
                                gr.getGroup() != null ? gr.getGroup().getGroupId() : null,
                                gr.getRole() != null ? gr.getRole().getRoleId() : null);
                        gr.setId(id);
                    }
                    LocalDateTime assignedAt = gr.getAssignedAt() == null ? LocalDateTime.now() : gr.getAssignedAt();
                    gr.setAssignedAt(assignedAt);
                    return new MapSqlParameterSource()
                            .addValue("groupId", id.getGroupId())
                            .addValue("roleId", id.getRoleId())
                            .addValue("assignedAt", Timestamp.valueOf(assignedAt))
                            .addValue("assignedBy", gr.getAssignedBy());
                })
                .toArray(SqlParameterSource[]::new);
        namedTemplate.batchUpdate(sql, batch);
        return buffer;
    }

    @Override
    public void deleteById(ApplicationGroupRoleId id) {
        if (id == null) {
            return;
        }
        String sql = """
                delete from TB_APPLICATION_GROUP_ROLES
                 where GROUP_ID = :groupId
                   and ROLE_ID = :roleId
                """;
        namedTemplate.update(sql, Map.of("groupId", id.getGroupId(), "roleId", id.getRoleId()));
    }

    private void loadGroupProperties(List<ApplicationGroup> groups) {
        List<Long> ids = groups.stream()
                .map(ApplicationGroup::getGroupId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties("TB_APPLICATION_GROUP_PROPERTY", "GROUP_ID", ids);
        for (ApplicationGroup group : groups) {
            Map<String, String> map = props.get(group.getGroupId());
            group.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
        }
    }

    private static String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private record JdbcGroupWithMemberCount(ApplicationGroup entity, Long memberCount)
            implements ApplicationGroupWithMemberCount {
        @Override
        public ApplicationGroup getEntity() {
            return entity;
        }

        @Override
        public Long getMemberCount() {
            return memberCount;
        }
    }
}
