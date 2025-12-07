package studio.one.base.user.persistence.jdbc;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.UserIdOnly;
import studio.one.base.user.persistence.ApplicationUserRepository;

@Repository
@ConditionalOnMissingBean(ApplicationUserRepository.class)
public class ApplicationUserJdbcRepository extends BaseJdbcRepository implements ApplicationUserRepository {

    private static final String TABLE = "TB_APPLICATION_USER";
    private static final String PROPERTY_TABLE = "TB_APPLICATION_USER_PROPERTY";

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("userId", "USER_ID"),
            Map.entry("username", "USERNAME"),
            Map.entry("name", "NAME"),
            Map.entry("firstName", "FIRST_NAME"),
            Map.entry("lastName", "LAST_NAME"),
            Map.entry("email", "EMAIL"),
            Map.entry("creationDate", "CREATION_DATE"),
            Map.entry("modifiedDate", "MODIFIED_DATE"),
            Map.entry("status", "STATUS"));

    private static final RowMapper<ApplicationUser> USER_ROW_MAPPER = JdbcUserMapper::mapBasicUser;

    private static final RowMapper<ApplicationGroup> GROUP_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationGroup group = new ApplicationGroup();
        group.setGroupId(rs.getLong("GROUP_ID"));
        group.setName(rs.getString("NAME"));
        group.setDescription(rs.getString("DESCRIPTION"));
        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        group.setCreationDate(created == null ? null : created.toInstant());
        group.setModifiedDate(modified == null ? null : modified.toInstant());
        group.setProperties(new HashMap<>());
        group.setMemberCount(0L);
        return group;
    };

    private final SimpleJdbcInsert insert;

    public ApplicationUserJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate)
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("USER_ID");
    }

    @Override
    public Page<ApplicationUser> findAll(Pageable pageable) {
        String select = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                """;
        String count = "select count(*) from TB_APPLICATION_USER";
        Page<ApplicationUser> page = queryPage(select, count, Map.of(), pageable, USER_ROW_MAPPER, "USER_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public Optional<ApplicationUser> findById(Long userId) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                 where USER_ID = :userId
                """;
        Optional<ApplicationUser> result = queryOptional(sql, Map.of("userId", userId), USER_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<ApplicationUser> findByUsername(String username) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                 where lower(USERNAME) = lower(:username)
                """;
        Optional<ApplicationUser> result = queryOptional(sql, Map.of("username", username), USER_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<ApplicationUser> findByUsernameForUpdate(String username) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                 where lower(USERNAME) = lower(:username)
                 for update
                """;
        Optional<ApplicationUser> result = queryOptional(sql, Map.of("username", username), USER_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<ApplicationUser> findByEmail(String email) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                 where lower(EMAIL) = lower(:email)
                """;
        Optional<ApplicationUser> result = queryOptional(sql, Map.of("email", email), USER_ROW_MAPPER);
        result.ifPresent(this::loadProperties);
        return result;
    }

    @Override
    public Optional<UserIdOnly> findFirstByUsernameIgnoreCase(String username) {
        String sql = "select USER_ID from TB_APPLICATION_USER where lower(USERNAME) = lower(:username)";
        return queryOptional(sql, Map.of("username", username),
                (rs, rowNum) -> new SimpleUserId(rs.getLong("USER_ID")));
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "select exists(select 1 from TB_APPLICATION_USER where lower(USERNAME) = lower(:username))";
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("username", username), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "select exists(select 1 from TB_APPLICATION_USER where lower(EMAIL) = lower(:email))";
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("email", email), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Page<ApplicationUser> findUsersByGroupId(Long groupId, Pageable pageable) {
        Map<String, Object> params = Map.of("groupId", groupId);
        String select = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                 where gm.GROUP_ID = :groupId
                """;
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where GROUP_ID = :groupId";
        Page<ApplicationUser> page = queryPage(select, count, params, pageable, USER_ROW_MAPPER, "u.USER_ID", SORT_COLUMNS);
        loadProperties(page.getContent());
        return page;
    }

    @Override
    public List<ApplicationUser> findUsersByGroupId(Long groupId) {
        String sql = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from TB_APPLICATION_USER u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                 where gm.GROUP_ID = :groupId
                """;
        List<ApplicationUser> users = namedTemplate.query(sql, Map.of("groupId", groupId), USER_ROW_MAPPER);
        loadProperties(users);
        return users;
    }

    @Override
    public Page<ApplicationUser> search(String keyword, Pageable pageable) {
        Map<String, Object> params = Map.of("q", normalize(keyword));
        String select = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from TB_APPLICATION_USER
                 where (:q = '' or
                       lower(USERNAME) like :q or
                       lower(NAME) like :q or
                       lower(EMAIL) like :q)
                """;
        String count = """
                select count(*)
                  from TB_APPLICATION_USER
                 where (:q = '' or
                       lower(USERNAME) like :q or
                       lower(NAME) like :q or
                       lower(EMAIL) like :q)
                """;
        Page<ApplicationUser> page = queryPage(select, count, params, pageable, USER_ROW_MAPPER, "USER_ID", SORT_COLUMNS);
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
        loadGroupProperties(groups);
        return groups;
    }

    @Override
    public List<Long> findGroupIdsByUserId(Long userId) {
        String sql = "select GROUP_ID from TB_APPLICATION_GROUP_MEMBERS where USER_ID = :userId";
        return namedTemplate.query(sql, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("GROUP_ID"));
    }

    @Override
    public ApplicationUser save(ApplicationUser user) {
        if (user.getUserId() == null) {
            return insert(user);
        }
        return update(user);
    }

    private ApplicationUser insert(ApplicationUser user) {
        Instant now = Instant.now();
        if (user.getCreationDate() == null) {
            user.setCreationDate(now);
        }
        if (user.getModifiedDate() == null) {
            user.setModifiedDate(user.getCreationDate());
        }
        MapSqlParameterSource params = toParameterSource(user, true);
        Number key = insert.executeAndReturnKey(params);
        user.setUserId(key.longValue());
        replaceProperties(PROPERTY_TABLE, "USER_ID", user.getUserId(), user.getProperties());
        return user;
    }

    private ApplicationUser update(ApplicationUser user) {
        if (user.getModifiedDate() == null) {
            user.setModifiedDate(Instant.now());
        }
        String sql = """
                update TB_APPLICATION_USER set
                    USERNAME = :username,
                    NAME = :name,
                    FIRST_NAME = :firstName,
                    LAST_NAME = :lastName,
                    PASSWORD_HASH = :password,
                    NAME_VISIBLE = :nameVisible,
                    EMAIL = :email,
                    EMAIL_VISIBLE = :emailVisible,
                    USER_ENABLED = :enabled,
                    USER_EXTERNAL = :external,
                    STATUS = :status,
                    FAILED_ATTEMPTS = :failedAttempts,
                    LAST_FAILED_AT = :lastFailedAt,
                    ACCOUNT_LOCKED_UNTIL = :accountLockedUntil,
                    MODIFIED_DATE = :modifiedDate
                 where USER_ID = :userId
                """;
        Map<String, Object> params = parameterMap(user);
        namedTemplate.update(sql, params);
        replaceProperties(PROPERTY_TABLE, "USER_ID", user.getUserId(), user.getProperties());
        return user;
    }

    @Override
    public void delete(ApplicationUser user) {
        if (user != null && user.getUserId() != null) {
            deleteById(user.getUserId());
        }
    }
 
    public void deleteById(Long userId) {
        namedTemplate.update("delete from TB_APPLICATION_USER where USER_ID = :userId", Map.of("userId", userId));
    }

    private MapSqlParameterSource toParameterSource(ApplicationUser user, boolean withTimestamps) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("USERNAME", user.getUsername())
                .addValue("NAME", user.getName())
                .addValue("FIRST_NAME", user.getFirstName())
                .addValue("LAST_NAME", user.getLastName())
                .addValue("PASSWORD_HASH", user.getPassword())
                .addValue("NAME_VISIBLE", user.isNameVisible())
                .addValue("EMAIL", user.getEmail())
                .addValue("EMAIL_VISIBLE", user.isEmailVisible())
                .addValue("USER_ENABLED", user.isEnabled())
                .addValue("USER_EXTERNAL", user.isExternal())
                .addValue("STATUS", user.getStatus() == null ? null : user.getStatus().ordinal())
                .addValue("FAILED_ATTEMPTS", user.getFailedAttempts())
                .addValue("LAST_FAILED_AT", toTimestamp(user.getLastFailedAt()))
                .addValue("ACCOUNT_LOCKED_UNTIL", toTimestamp(user.getAccountLockedUntil()));
        if (withTimestamps) {
            params.addValue("CREATION_DATE", Timestamp.from(user.getCreationDate()));
            params.addValue("MODIFIED_DATE", Timestamp.from(user.getModifiedDate()));
        }
        return params;
    }

    private Map<String, Object> parameterMap(ApplicationUser user) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", user.getUserId());
        params.put("username", user.getUsername());
        params.put("name", user.getName());
        params.put("firstName", user.getFirstName());
        params.put("lastName", user.getLastName());
        params.put("password", user.getPassword());
        params.put("nameVisible", user.isNameVisible());
        params.put("email", user.getEmail());
        params.put("emailVisible", user.isEmailVisible());
        params.put("enabled", user.isEnabled());
        params.put("external", user.isExternal());
        params.put("status", user.getStatus() == null ? null : user.getStatus().ordinal());
        params.put("failedAttempts", user.getFailedAttempts());
        params.put("lastFailedAt", toTimestamp(user.getLastFailedAt()));
        params.put("accountLockedUntil", toTimestamp(user.getAccountLockedUntil()));
        params.put("modifiedDate", Timestamp.from(user.getModifiedDate()));
        return params;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private void loadProperties(ApplicationUser user) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        Map<Long, Map<String, String>> props = fetchProperties(PROPERTY_TABLE, "USER_ID", List.of(user.getUserId()));
        user.setProperties(new HashMap<>(props.getOrDefault(user.getUserId(), Map.of())));
    }

    private void loadProperties(List<ApplicationUser> users) {
        List<Long> ids = users.stream()
                .map(ApplicationUser::getUserId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, String>> allProps = fetchProperties(PROPERTY_TABLE, "USER_ID", ids);
        for (ApplicationUser user : users) {
            Map<String, String> props = allProps.get(user.getUserId());
            user.setProperties(props == null ? new HashMap<>() : new HashMap<>(props));
        }
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
        return "%" + keyword.toLowerCase() + "%";
    }

    private record SimpleUserId(Long userId) implements UserIdOnly {
        @Override
        public Long getUserId() {
            return userId;
        }
    }
}
