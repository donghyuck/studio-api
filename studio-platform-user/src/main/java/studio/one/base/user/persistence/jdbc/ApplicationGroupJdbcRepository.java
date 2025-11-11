package studio.one.base.user.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.persistence.ApplicationGroupRepository;

@Repository
public class ApplicationGroupJdbcRepository extends BaseJdbcRepository implements ApplicationGroupRepository {

    private static final String TABLE = "TB_APPLICATION_GROUP";
    private static final String PROPERTY_TABLE = "TB_APPLICATION_GROUP_PROPERTY";

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "groupId", "GROUP_ID",
            "name", "NAME",
            "description", "DESCRIPTION",
            "creationDate", "CREATION_DATE",
            "modifiedDate", "MODIFIED_DATE");

    private static final RowMapper<ApplicationGroup> GROUP_ROW_MAPPER = (rs, rowNum) -> mapGroup(rs);

    private static final RowMapper<ApplicationGroupWithMemberCount> GROUP_WITH_COUNT_MAPPER = (rs, rowNum) -> {
        ApplicationGroup group = mapGroup(rs);
        long count = rs.getLong("MEMBER_COUNT");
        group.setMemberCount(rs.wasNull() ? 0L : count);
        return new JdbcApplicationGroupWithMemberCount(group, group.getMemberCount());
    };

    private final SimpleJdbcInsert insert;

    public ApplicationGroupJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate)
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("GROUP_ID");
    }

    @Override
    public Page<ApplicationGroup> findAll(Pageable pageable) {
        String select = """
                select GROUP_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_GROUP
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP";
        Page<ApplicationGroup> page = queryPage(select, count, Map.of(), pageable, GROUP_ROW_MAPPER, "GROUP_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Optional<ApplicationGroup> findById(Long groupId) {
        String sql = """
                select GROUP_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_GROUP
                 where GROUP_ID = :groupId
                """;
        Optional<ApplicationGroup> result = queryOptional(sql, Map.of("groupId", groupId), GROUP_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public List<ApplicationGroup> findAll() {
        String sql = """
                select GROUP_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_GROUP
                  order by GROUP_ID
                """;
        List<ApplicationGroup> groups = namedTemplate.query(sql, GROUP_ROW_MAPPER);
        loadProperties(groups);
        return groups;
    }

    @Override
    public Page<ApplicationGroup> findGroupsByUserId(Long userId, Pageable pageable) {
        Map<String, Object> params = Map.of("userId", userId);
        String select = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.GROUP_ID = g.GROUP_ID
                 where gm.USER_ID = :userId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where USER_ID = :userId";
        Page<ApplicationGroup> page = queryPage(select, count, params, pageable, GROUP_ROW_MAPPER, "g.GROUP_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public List<ApplicationGroup> findGroupsByUserId(Long userId) {
        String sql = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.GROUP_ID = g.GROUP_ID
                 where gm.USER_ID = :userId
                """;
        List<ApplicationGroup> groups = namedTemplate.query(sql, Map.of("userId", userId), GROUP_ROW_MAPPER);
        loadProperties(groups);
        return groups;
    }

    @Override
    public List<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByUserId(Long userId) {
        String sql = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE,
                       count(allm.USER_ID) as MEMBER_COUNT
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.GROUP_ID = g.GROUP_ID and gm.USER_ID = :userId
                  left join TB_APPLICATION_GROUP_MEMBERS allm on allm.GROUP_ID = g.GROUP_ID
                 group by g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                 order by g.GROUP_ID desc
                """;
        List<ApplicationGroupWithMemberCount> rows = namedTemplate.query(sql, Map.of("userId", userId), GROUP_WITH_COUNT_MAPPER);
        rows.forEach(row -> loadProperties(row.getEntity()));
        return rows;
    }

    @Override
    public Page<ApplicationGroup> findGroupsByName(String keyword, Pageable pageable) {
        Map<String, Object> params = Map.of("q", normalizeKeyword(keyword));
        String select = """
                select GROUP_ID, NAME, DESCRIPTION, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_GROUP
                 where (:q = '' or lower(NAME) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_GROUP
                 where (:q = '' or lower(NAME) like :q)
                """;
        Page<ApplicationGroup> page = queryPage(select, count, params, pageable, GROUP_ROW_MAPPER, "GROUP_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Page<ApplicationGroupWithMemberCount> findGroupsWithMemberCountByName(String keyword, Pageable pageable) {
        Map<String, Object> params = Map.of("q", normalizeKeyword(keyword));
        String select = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE,
                       count(m.USER_ID) as MEMBER_COUNT
                  from TB_APPLICATION_GROUP g
                  left join TB_APPLICATION_GROUP_MEMBERS m on m.GROUP_ID = g.GROUP_ID
                 where (:q = '' or lower(g.NAME) like :q)
                 group by g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_GROUP
                 where (:q = '' or lower(NAME) like :q)
                """;
        Page<ApplicationGroupWithMemberCount> page = queryPage(select, count, params, pageable, GROUP_WITH_COUNT_MAPPER, "g.GROUP_ID", SORT_COLUMNS);
        page.getContent().forEach(row -> loadProperties(row.getEntity()));
        return page;
    }

    @Override
    public ApplicationGroup save(ApplicationGroup group) {
        if (group.getGroupId() == null) {
            return insert(group);
        }
        return update(group);
    }

    private ApplicationGroup insert(ApplicationGroup group) {
        Instant now = Instant.now();
        if (group.getCreationDate() == null) {
            group.setCreationDate(now);
        }
        if (group.getModifiedDate() == null) {
            group.setModifiedDate(group.getCreationDate());
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("NAME", group.getName())
                .addValue("DESCRIPTION", group.getDescription())
                .addValue("CREATION_DATE", Timestamp.from(group.getCreationDate()))
                .addValue("MODIFIED_DATE", Timestamp.from(group.getModifiedDate()));
        Number key = insert.executeAndReturnKey(params);
        group.setGroupId(key.longValue());
        replaceProperties(PROPERTY_TABLE, "GROUP_ID", group.getGroupId(), group.getProperties());
        return group;
    }

    private ApplicationGroup update(ApplicationGroup group) {
        if (group.getModifiedDate() == null) {
            group.setModifiedDate(Instant.now());
        }
        String sql = """
                update TB_APPLICATION_GROUP
                   set NAME = :name,
                       DESCRIPTION = :description,
                       MODIFIED_DATE = :modifiedDate
                 where GROUP_ID = :groupId
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("name", group.getName());
        params.put("description", group.getDescription());
        params.put("modifiedDate", Timestamp.from(group.getModifiedDate()));
        params.put("groupId", group.getGroupId());
        namedTemplate.update(sql, params);
        replaceProperties(PROPERTY_TABLE, "GROUP_ID", group.getGroupId(), group.getProperties());
        return group;
    }

    @Override
    public void delete(ApplicationGroup group) {
        if (group != null && group.getGroupId() != null) {
            deleteById(group.getGroupId());
        }
    }

    @Override
    public void deleteById(Long groupId) {
        namedTemplate.update("delete from TB_APPLICATION_GROUP where GROUP_ID = :groupId", Map.of("groupId", groupId));
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "select exists(select 1 from TB_APPLICATION_GROUP where lower(NAME) = lower(:name))";
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("name", name), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private void loadProperties(ApplicationGroup group) {
        if (group == null || group.getGroupId() == null) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(PROPERTY_TABLE, "GROUP_ID", List.of(group.getGroupId()));
        group.setProperties(new HashMap<>(props.getOrDefault(group.getGroupId(), Map.of())));
    }

    private void loadProperties(List<ApplicationGroup> groups) {
        List<Long> ids = groups.stream()
                .map(ApplicationGroup::getGroupId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(PROPERTY_TABLE, "GROUP_ID", ids);
        for (ApplicationGroup group : groups) {
            Map<String, String> map = props.get(group.getGroupId());
            group.setProperties(map == null ? new HashMap<>() : new HashMap<>(map));
        }
    }

    private static ApplicationGroup mapGroup(ResultSet rs) throws SQLException {
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
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.toLowerCase() + "%";
    }

    private record JdbcApplicationGroupWithMemberCount(ApplicationGroup entity, Long memberCount)
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
