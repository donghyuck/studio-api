package studio.one.base.user.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.persistence.ApplicationRoleRepository;

@Repository(ApplicationRoleRepository.SERVICE_NAME)
public class ApplicationRoleJdbcRepository extends BaseJdbcRepository implements ApplicationRoleRepository {

    private static final String TABLE = "TB_APPLICATION_ROLE";

    private static final Map<String, String> ROLE_SORT_COLUMNS = Map.of(
            "roleId", "ROLE_ID",
            "name", "NAME",
            "description", "DESCRIPTION",
            "creationDate", "CREATION_DATE",
            "modifiedDate", "MODIFIED_DATE");

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

    private final SimpleJdbcInsert insert;

    public ApplicationRoleJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate)
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("ROLE_ID");
    }

    @Override
    public Page<ApplicationRole> findAll(Pageable pageable) {
        String select = "select ROLE_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE from " + TABLE;
        String count = "select count(*) from " + TABLE;
        return queryPage(select, count, Map.of(), pageable, ROLE_ROW_MAPPER, "ROLE_ID desc", ROLE_SORT_COLUMNS);
    }

    @Override
    public List<ApplicationRole> findAll(Sort sort) {
        String orderBy = buildOrderByClause(sort, "ROLE_ID", ROLE_SORT_COLUMNS);
        String sql = "select ROLE_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE from " + TABLE + orderBy;
        return namedTemplate.query(sql, Map.of(), ROLE_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationRole> findById(Long roleId) {
        String sql = """
                select ROLE_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_ROLE
                 where ROLE_ID = :roleId
                """;
        return queryOptional(sql, Map.of("roleId", roleId), ROLE_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationRole> findByName(String name) {
        String sql = """
                select ROLE_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_ROLE
                 where lower(NAME) = lower(:name)
                """;
        return queryOptional(sql, Map.of("name", name), ROLE_ROW_MAPPER);
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "select exists(select 1 from TB_APPLICATION_ROLE where lower(NAME) = lower(:name))";
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("name", name), Boolean.class);
        return Boolean.TRUE.equals(exists);
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
    public Page<ApplicationRole> findRolesByUserId(Long userId, Pageable pageable) {
        Map<String, Object> params = Map.of("userId", userId);
        String select = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_USER_ROLES ur on ur.ROLE_ID = r.ROLE_ID
                 where ur.USER_ID = :userId
                """;
        String count = "select count(*) from TB_APPLICATION_USER_ROLES where USER_ID = :userId";
        return queryPage(select, count, params, pageable, ROLE_ROW_MAPPER, "r.ROLE_ID", ROLE_SORT_COLUMNS);
    }

    @Override
    public List<ApplicationRole> findRolesByGroupIds(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        String sql = """
                select distinct r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_GROUP_ROLES gr on gr.ROLE_ID = r.ROLE_ID
                 where gr.GROUP_ID in (:groupIds)
                """;
        return namedTemplate.query(sql, Map.of("groupIds", groupIds), ROLE_ROW_MAPPER);
    }

    @Override
    public Page<ApplicationRole> findRolesByGroupId(Long groupId, Pageable pageable) {
        Map<String, Object> params = Map.of("groupId", groupId);
        String select = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_GROUP_ROLES gr on gr.ROLE_ID = r.ROLE_ID
                 where gr.GROUP_ID = :groupId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_ROLES where GROUP_ID = :groupId";
        return queryPage(select, count, params, pageable, ROLE_ROW_MAPPER, "r.ROLE_ID", ROLE_SORT_COLUMNS);
    }

    @Override
    public List<ApplicationRole> findRolesByGroupId(Long groupId, Sort sort) {
        String orderBy = buildOrderByClause(sort, "r.ROLE_ID", ROLE_SORT_COLUMNS);
        String sql = """
                select r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                  join TB_APPLICATION_GROUP_ROLES gr on gr.ROLE_ID = r.ROLE_ID
                 where gr.GROUP_ID = :groupId
                """ + orderBy;
        return namedTemplate.query(sql, Map.of("groupId", groupId), ROLE_ROW_MAPPER);
    }

    @Override
    public List<ApplicationRole> findEffectiveRolesByUserId(Long userId) {
        String sql = """
                select distinct r.ROLE_ID, r.NAME, r.DESCRIPTION, r.CREATION_DATE, r.MODIFIED_DATE
                  from TB_APPLICATION_ROLE r
                 where exists (
                        select 1
                          from TB_APPLICATION_USER_ROLES ur
                         where ur.ROLE_ID = r.ROLE_ID
                           and ur.USER_ID = :userId
                 )
                    or exists (
                        select 1
                          from TB_APPLICATION_GROUP_ROLES gr
                          join TB_APPLICATION_GROUP_MEMBERS gm on gm.GROUP_ID = gr.GROUP_ID
                         where gr.ROLE_ID = r.ROLE_ID
                           and gm.USER_ID = :userId
                 )
                """;
        return namedTemplate.query(sql, Map.of("userId", userId), ROLE_ROW_MAPPER);
    }

    @Override
    public List<Long> findExistingIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String sql = "select ROLE_ID from TB_APPLICATION_ROLE where ROLE_ID in (:ids)";
        return namedTemplate.query(sql, Map.of("ids", ids),
                (rs, rowNum) -> rs.getLong("ROLE_ID"));
    }

    @Override
    public ApplicationRole save(ApplicationRole role) {
        if (role.getRoleId() == null) {
            return insert(role);
        }
        return update(role);
    }

    private ApplicationRole insert(ApplicationRole role) {
        Instant now = Instant.now();
        if (role.getCreationDate() == null) {
            role.setCreationDate(now);
        }
        if (role.getModifiedDate() == null) {
            role.setModifiedDate(role.getCreationDate());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("NAME", role.getName())
                .addValue("DESCRIPTION", role.getDescription())
                .addValue("CREATION_DATE", Timestamp.from(role.getCreationDate()))
                .addValue("MODIFIED_DATE", Timestamp.from(role.getModifiedDate()));
        Number key = insert.executeAndReturnKey(params);
        role.setRoleId(key.longValue());
        return role;
    }

    private ApplicationRole update(ApplicationRole role) {
        if (role.getModifiedDate() == null) {
            role.setModifiedDate(Instant.now());
        }
        String sql = """
                update TB_APPLICATION_ROLE
                   set NAME = :name,
                       DESCRIPTION = :description,
                       MODIFIED_DATE = :modifiedDate
                 where ROLE_ID = :roleId
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("name", role.getName());
        params.put("description", role.getDescription());
        params.put("modifiedDate", Timestamp.from(role.getModifiedDate()));
        params.put("roleId", role.getRoleId());
        namedTemplate.update(sql, params);
        return role;
    }

    @Override
    public void delete(ApplicationRole role) {
        if (role != null && role.getRoleId() != null) {
            deleteById(role.getRoleId());
        }
    }

    @Override
    public void deleteById(Long roleId) {
        namedTemplate.update("delete from TB_APPLICATION_ROLE where ROLE_ID = :roleId", Map.of("roleId", roleId));
    }
}
