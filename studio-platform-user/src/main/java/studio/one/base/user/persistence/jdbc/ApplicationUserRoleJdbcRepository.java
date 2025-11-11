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

import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.entity.ApplicationUserRole;
import studio.one.base.user.domain.entity.ApplicationUserRoleId;
import studio.one.base.user.persistence.ApplicationUserRoleRepository;

@Repository
public class ApplicationUserRoleJdbcRepository extends BaseJdbcRepository implements ApplicationUserRoleRepository {

    private static final Map<String, String> USER_SORT_COLUMNS = Map.of(
            "userId", "u.USER_ID",
            "username", "u.USERNAME",
            "name", "u.NAME",
            "email", "u.EMAIL",
            "creationDate", "u.CREATION_DATE");

    private static final RowMapper<ApplicationUserRole> USER_ROLE_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationUserRole userRole = new ApplicationUserRole();
        ApplicationUserRoleId id = new ApplicationUserRoleId(rs.getLong("USER_ID"), rs.getLong("ROLE_ID"));
        userRole.setId(id);

        ApplicationUser user = new ApplicationUser();
        user.setUserId(id.getUserId());
        userRole.setUser(user);

        ApplicationRole role = new ApplicationRole();
        role.setRoleId(id.getRoleId());
        userRole.setRole(role);

        Timestamp assignedAt = rs.getTimestamp("ASSIGNED_AT");
        userRole.setAssignedAt(assignedAt == null ? LocalDateTime.now() : assignedAt.toLocalDateTime());
        userRole.setAssignedBy(rs.getString("ASSIGNED_BY"));
        return userRole;
    };

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

    private static final RowMapper<ApplicationUser> USER_ROW_MAPPER = JdbcUserMapper::mapBasicUser;

    public ApplicationUserRoleJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public List<ApplicationUserRole> findAllByUserId(Long userId) {
        String sql = """
                select USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY
                  from TB_APPLICATION_USER_ROLES
                 where USER_ID = :userId
                """;
        return namedTemplate.query(sql, Map.of("userId", userId), USER_ROLE_ROW_MAPPER);
    }

    @Override
    public Page<ApplicationUserRole> findAllByUserId(Long userId, Pageable pageable) {
        Map<String, Object> params = Map.of("userId", userId);
        String select = """
                select USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY
                  from TB_APPLICATION_USER_ROLES
                 where USER_ID = :userId
                """;
        String count = "select count(*) from TB_APPLICATION_USER_ROLES where USER_ID = :userId";
        return queryPage(select, count, params, pageable, USER_ROLE_ROW_MAPPER, "USER_ID", Map.of());
    }

    @Override
    public List<ApplicationUserRole> findAllByRoleId(Long roleId) {
        String sql = """
                select USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY
                  from TB_APPLICATION_USER_ROLES
                 where ROLE_ID = :roleId
                """;
        return namedTemplate.query(sql, Map.of("roleId", roleId), USER_ROLE_ROW_MAPPER);
    }

    @Override
    public Page<ApplicationUserRole> findAllByRoleId(Long roleId, Pageable pageable) {
        Map<String, Object> params = Map.of("roleId", roleId);
        String select = """
                select USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY
                  from TB_APPLICATION_USER_ROLES
                 where ROLE_ID = :roleId
                """;
        String count = "select count(*) from TB_APPLICATION_USER_ROLES where ROLE_ID = :roleId";
        return queryPage(select, count, params, pageable, USER_ROLE_ROW_MAPPER, "USER_ID", Map.of());
    }

    @Override
    public boolean existsByUserIdAndRoleId(Long userId, Long roleId) {
        String sql = """
                select exists(
                    select 1 from TB_APPLICATION_USER_ROLES
                     where USER_ID = :userId
                       and ROLE_ID = :roleId)
                """;
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("userId", userId, "roleId", roleId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int deleteByUserIdAndRoleId(Long userId, Long roleId) {
        String sql = """
                delete from TB_APPLICATION_USER_ROLES
                 where USER_ID = :userId
                   and ROLE_ID = :roleId
                """;
        return namedTemplate.update(sql, Map.of("userId", userId, "roleId", roleId));
    }

    @Override
    public int deleteByUserIdAndRoleIds(Long userId, Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return 0;
        }
        String sql = """
                delete from TB_APPLICATION_USER_ROLES
                 where USER_ID = :userId
                   and ROLE_ID in (:roleIds)
                """;
        return namedTemplate.update(sql, Map.of("userId", userId, "roleIds", roleIds));
    }

    @Override
    public int deleteByUserIdsAndRoleId(Collection<Long> userIds, Long roleId) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        String sql = """
                delete from TB_APPLICATION_USER_ROLES
                 where USER_ID in (:userIds)
                   and ROLE_ID = :roleId
                """;
        return namedTemplate.update(sql, Map.of("userIds", userIds, "roleId", roleId));
    }

    @Override
    public Page<ApplicationRole> findRolesByUserId(Long userId, Pageable pageable) {
        Map<String, Object> params = Map.of("userId", userId);
        String select = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_USER_ROLES ur on ur.ROLE_ID = r.ROLE_ID
                 where ur.USER_ID = :userId
                """;
        String count = "select count(*) from TB_APPLICATION_USER_ROLES where USER_ID = :userId";
        return queryPage(select, count, params, pageable, ROLE_ROW_MAPPER, "r.ROLE_ID", Map.of(
                "roleId", "r.ROLE_ID",
                "name", "r.NAME"));
    }

    @Override
    public List<ApplicationRole> findRolesByUserId(Long userId) {
        String sql = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_USER_ROLES ur on ur.ROLE_ID = r.ROLE_ID
                 where ur.USER_ID = :userId
                """;
        return namedTemplate.query(sql, Map.of("userId", userId), ROLE_ROW_MAPPER);
    }

    @Override
    public List<Long> findRoleIdsByUserId(Long userId) {
        String sql = "select ROLE_ID from TB_APPLICATION_USER_ROLES where USER_ID = :userId";
        return namedTemplate.query(sql, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("ROLE_ID"));
    }

    @Override
    public Page<ApplicationUser> findUsersByRoleId(Long roleId, Pageable pageable) {
        Map<String, Object> params = Map.of("roleId", roleId);
        String select = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_USER_ROLES ur on ur.USER_ID = u.USER_ID
                 where ur.ROLE_ID = :roleId
                """;
        String count = "select count(*) from TB_APPLICATION_USER_ROLES where ROLE_ID = :roleId";
        Page<ApplicationUser> page = queryPage(select, count, params, pageable, USER_ROW_MAPPER, "u.USER_ID", USER_SORT_COLUMNS);
        loadUserProperties(page.getContent());
        return page;
    }

    @Override
    public List<ApplicationUser> findUsersByRoleId(Long roleId) {
        String sql = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_USER_ROLES ur on ur.USER_ID = u.USER_ID
                 where ur.ROLE_ID = :roleId
                """;
        List<ApplicationUser> users = namedTemplate.query(sql, Map.of("roleId", roleId), USER_ROW_MAPPER);
        loadUserProperties(users);
        return users;
    }

    @Override
    public Page<ApplicationUser> findUsersByRoleId(Long roleId, String keyword, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("roleId", roleId);
        params.put("q", normalize(keyword));
        String select = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_USER_ROLES ur on ur.USER_ID = u.USER_ID
                 where ur.ROLE_ID = :roleId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_USER_ROLES ur on ur.USER_ID = u.USER_ID
                 where ur.ROLE_ID = :roleId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        Page<ApplicationUser> page = queryPage(select, count, params, pageable, USER_ROW_MAPPER, "u.USER_ID", USER_SORT_COLUMNS);
        loadUserProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationUser> findUsersByRoleIdViaGroup(Long roleId, String keyword, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("roleId", roleId);
        params.put("q", normalize(keyword));
        String select = """
                select distinct u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = gm.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        String count = """
                select count(distinct u.USER_ID)
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                  join TB_APPLICATION_GROUP_ROLES gr on gr.GROUP_ID = gm.GROUP_ID
                 where gr.ROLE_ID = :roleId
                   and (:q = '' or lower(u.USERNAME) like :q or lower(u.NAME) like :q or lower(u.EMAIL) like :q)
                """;
        Page<ApplicationUser> page = queryPage(select, count, params, pageable, USER_ROW_MAPPER, "u.USER_ID", USER_SORT_COLUMNS);
        loadUserProperties(page.getContent());
        return page;
    }

    @Override
    public ApplicationUserRole save(ApplicationUserRole userRole) {
        ApplicationUserRoleId id = userRole.getId();
        if (id == null) {
            id = new ApplicationUserRoleId(
                    userRole.getUser() != null ? userRole.getUser().getUserId() : null,
                    userRole.getRole() != null ? userRole.getRole().getRoleId() : null);
            userRole.setId(id);
        }
        LocalDateTime assignedAt = userRole.getAssignedAt();
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
            userRole.setAssignedAt(assignedAt);
        }
        String sql = """
                insert into TB_APPLICATION_USER_ROLES (USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY)
                values (:userId, :roleId, :assignedAt, :assignedBy)
                on conflict (USER_ID, ROLE_ID) do update
                      set ASSIGNED_AT = excluded.ASSIGNED_AT,
                          ASSIGNED_BY = excluded.ASSIGNED_BY
                """;
        namedTemplate.update(sql, Map.of(
                "userId", id.getUserId(),
                "roleId", id.getRoleId(),
                "assignedAt", Timestamp.valueOf(assignedAt),
                "assignedBy", userRole.getAssignedBy()));
        return userRole;
    }

    @Override
    public <S extends ApplicationUserRole> List<S> saveAll(Iterable<S> userRoles) {
        List<S> buffer = new ArrayList<>();
        userRoles.forEach(buffer::add);
        if (buffer.isEmpty()) {
            return buffer;
        }
        String sql = """
                insert into TB_APPLICATION_USER_ROLES (USER_ID, ROLE_ID, ASSIGNED_AT, ASSIGNED_BY)
                values (:userId, :roleId, :assignedAt, :assignedBy)
                on conflict (USER_ID, ROLE_ID) do update
                      set ASSIGNED_AT = excluded.ASSIGNED_AT,
                          ASSIGNED_BY = excluded.ASSIGNED_BY
                """;
        SqlParameterSource[] batch = buffer.stream()
                .map(ur -> {
                    ApplicationUserRoleId id = ur.getId();
                    if (id == null) {
                        id = new ApplicationUserRoleId(
                                ur.getUser() != null ? ur.getUser().getUserId() : null,
                                ur.getRole() != null ? ur.getRole().getRoleId() : null);
                        ur.setId(id);
                    }
                    LocalDateTime assignedAt = ur.getAssignedAt() == null ? LocalDateTime.now() : ur.getAssignedAt();
                    ur.setAssignedAt(assignedAt);
                    return new MapSqlParameterSource()
                            .addValue("userId", id.getUserId())
                            .addValue("roleId", id.getRoleId())
                            .addValue("assignedAt", Timestamp.valueOf(assignedAt))
                            .addValue("assignedBy", ur.getAssignedBy());
                })
                .toArray(SqlParameterSource[]::new);
        namedTemplate.batchUpdate(sql, batch);
        return buffer;
    }

    @Override
    public boolean existsById(ApplicationUserRoleId id) {
        if (id == null) {
            return false;
        }
        String sql = """
                select exists(
                    select 1 from TB_APPLICATION_USER_ROLES
                     where USER_ID = :userId
                       and ROLE_ID = :roleId)
                """;
        Boolean exists = namedTemplate.queryForObject(sql, Map.of(
                "userId", id.getUserId(),
                "roleId", id.getRoleId()), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private void loadUserProperties(List<ApplicationUser> users) {
        List<Long> ids = users.stream()
                .map(ApplicationUser::getUserId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties("TB_APPLICATION_USER_PROPERTY", "USER_ID", ids);
        for (ApplicationUser user : users) {
            Map<String, String> map = props.get(user.getUserId());
            user.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
        }
    }

    private static String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }
}
