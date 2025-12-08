package studio.one.custom.user.persistence.jdbc;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.model.UserIdOnly;
import studio.one.base.user.persistence.ApplicationUserRepository;
import studio.one.custom.user.domain.entity.CustomUser;

/**
 * CustomUser에 대한 JDBC 구현 예시.
 */
@Repository
public class CustomUserJdbcRepository implements ApplicationUserRepository {

    private static final String TABLE = "TB_CUSTOM_USER";
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "userId", "USER_ID",
            "username", "USERNAME",
            "name", "NAME",
            "email", "EMAIL",
            "creationDate", "CREATION_DATE",
            "modifiedDate", "MODIFIED_DATE",
            "status", "STATUS");

    private static final RowMapper<CustomUser> USER_ROW_MAPPER = (rs, rowNum) -> {
        CustomUser u = new CustomUser();
        u.setUserId(rs.getLong("USER_ID"));
        u.setUsername(rs.getString("USERNAME"));
        u.setName(rs.getString("NAME"));
        u.setFirstName(rs.getString("FIRST_NAME"));
        u.setLastName(rs.getString("LAST_NAME"));
        u.setPassword(rs.getString("PASSWORD_HASH"));
        u.setNameVisible(rs.getBoolean("NAME_VISIBLE"));
        u.setEmail(rs.getString("EMAIL"));
        u.setEmailVisible(rs.getBoolean("EMAIL_VISIBLE"));
        u.setEnabled(rs.getBoolean("USER_ENABLED"));
        u.setExternal(rs.getBoolean("USER_EXTERNAL"));
        u.setStatus(rs.getObject("STATUS") != null ? rs.getInt("STATUS") : null);
        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        u.setCreationDate(created == null ? null : created.toInstant());
        u.setModifiedDate(modified == null ? null : modified.toInstant());
        Timestamp failedAt = rs.getTimestamp("LAST_FAILED_AT");
        u.setLastFailedAt(failedAt == null ? null : failedAt.toInstant());
        Timestamp lockedUntil = rs.getTimestamp("ACCOUNT_LOCKED_UNTIL");
        u.setAccountLockedUntil(lockedUntil == null ? null : lockedUntil.toInstant());
        u.setFailedAttempts(rs.getInt("FAILED_ATTEMPTS"));
        u.setProperties(new HashMap<>());
        return u;
    };

    private final NamedParameterJdbcTemplate namedTemplate;

    public CustomUserJdbcRepository(NamedParameterJdbcTemplate template) {
        this.namedTemplate = template;
    }

    @Override
    public Page<CustomUser> findAll(Pageable pageable) {
        String select = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from %s
                """.formatted(TABLE);
        String count = "select count(*) from %s".formatted(TABLE);
        List<CustomUser> content = namedTemplate.query(select + buildOrderBy(pageable), Map.of(), USER_ROW_MAPPER);
        long total = namedTemplate.queryForObject(count, Map.of(), Long.class);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Optional<CustomUser> findById(Long userId) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from %s
                 where USER_ID = :userId
                """.formatted(TABLE);
        return queryOptional(sql, Map.of("userId", userId));
    }

    @Override
    public Optional<CustomUser> findByUsername(String username) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from %s
                 where lower(USERNAME) = lower(:username)
                """.formatted(TABLE);
        return queryOptional(sql, Map.of("username", username));
    }

    @Override
    public Optional<CustomUser> findByUsernameForUpdate(String username) {
        return findByUsername(username);
    }

    @Override
    public Optional<UserIdOnly> findFirstByUsernameIgnoreCase(String username) {
        String sql = "select USER_ID from %s where lower(USERNAME) = lower(:username)".formatted(TABLE);
        return queryOptional(sql, Map.of("username", username),
                (rs, rowNum) -> (UserIdOnly) () -> rs.getLong("USER_ID"));
    }

    @Override
    public Optional<CustomUser> findByEmail(String email) {
        String sql = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from %s
                 where lower(EMAIL) = lower(:email)
                """.formatted(TABLE);
        return queryOptional(sql, Map.of("email", email));
    }

    @Override
    public boolean existsByUsername(String username) {
        String sql = "select exists(select 1 from %s where lower(USERNAME)=lower(:username))".formatted(TABLE);
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("username", username), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "select exists(select 1 from %s where lower(EMAIL)=lower(:email))".formatted(TABLE);
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("email", email), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Page<CustomUser> findUsersByGroupId(Long groupId, Pageable pageable) {
        String select = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from %s u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                 where gm.GROUP_ID = :groupId
                """.formatted(TABLE);
        String count = "select count(*) from TB_APPLICATION_GROUP_MEMBERS where GROUP_ID = :groupId";
        return queryPage(select, count, Map.of("groupId", groupId), pageable);
    }

    @Override
    public List<CustomUser> findUsersByGroupId(Long groupId) {
        String sql = """
                select u.USER_ID, u.USERNAME, u.NAME, u.FIRST_NAME, u.LAST_NAME, u.PASSWORD_HASH,
                       u.NAME_VISIBLE, u.EMAIL, u.EMAIL_VISIBLE, u.USER_ENABLED, u.USER_EXTERNAL, u.STATUS,
                       u.FAILED_ATTEMPTS, u.LAST_FAILED_AT, u.ACCOUNT_LOCKED_UNTIL, u.CREATION_DATE, u.MODIFIED_DATE
                  from %s u
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.USER_ID = u.USER_ID
                 where gm.GROUP_ID = :groupId
                """.formatted(TABLE);
        return namedTemplate.query(sql, Map.of("groupId", groupId), USER_ROW_MAPPER);
    }

    @Override
    public Page<CustomUser> search(String keyword, Pageable pageable) {
        Map<String, Object> params = Map.of("q", normalize(keyword));
        String select = """
                select USER_ID, USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                       NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                       FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE
                  from %s
                 where (:q = '' or
                       lower(USERNAME) like :q or
                       lower(NAME) like :q or
                       lower(EMAIL) like :q)
                """.formatted(TABLE);
        String count = """
                select count(*)
                  from %s
                 where (:q = '' or
                       lower(USERNAME) like :q or
                       lower(NAME) like :q or
                       lower(EMAIL) like :q)
                """.formatted(TABLE);
        return queryPage(select, count, params, pageable);
    }

    @Override
    public List<ApplicationGroup> findGroupsByUserId(Long userId) {
        String sql = """
                select g.GROUP_ID, g.NAME, g.DESCRIPTION, g.CREATION_DATE, g.MODIFIED_DATE
                  from TB_APPLICATION_GROUP g
                  join TB_APPLICATION_GROUP_MEMBERS gm on gm.GROUP_ID = g.GROUP_ID
                 where gm.USER_ID = :userId
                """;
        return namedTemplate.query(sql, Map.of("userId", userId), (rs, rowNum) -> {
            ApplicationGroup g = new ApplicationGroup();
            g.setGroupId(rs.getLong("GROUP_ID"));
            g.setName(rs.getString("NAME"));
            g.setDescription(rs.getString("DESCRIPTION"));
            Timestamp created = rs.getTimestamp("CREATION_DATE");
            Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
            g.setCreationDate(created == null ? null : created.toInstant());
            g.setModifiedDate(modified == null ? null : modified.toInstant());
            g.setProperties(new HashMap<>());
            g.setMemberCount(0L);
            return g;
        });
    }

    @Override
    public List<Long> findGroupIdsByUserId(Long userId) {
        String sql = "select GROUP_ID from TB_APPLICATION_GROUP_MEMBERS where USER_ID = :userId";
        return namedTemplate.query(sql, Map.of("userId", userId), (rs, rowNum) -> rs.getLong("GROUP_ID"));
    }

    @Override
    public CustomUser save(CustomUser user) {
        if (user.getUserId() == null || user.getUserId() <= 0) {
            return insert(user);
        }
        return update(user);
    }

    @Override
    public void delete(CustomUser user) {
        if (user == null || user.getUserId() == null) return;
        namedTemplate.update("delete from %s where USER_ID = :id".formatted(TABLE), Map.of("id", user.getUserId()));
    }

    @Override
    public <S extends CustomUser> List<S> saveAll(Iterable<S> users) {
        List<S> list = new java.util.ArrayList<>();
        users.forEach(list::add);
        if (list.isEmpty()) return list;
        for (S u : list) {
            save(u);
        }
        return list;
    }

    @Override
    public boolean existsById(Long id) {
        String sql = "select exists(select 1 from %s where USER_ID = :id)".formatted(TABLE);
        Boolean exists = namedTemplate.queryForObject(sql, Map.of("id", id), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private CustomUser insert(CustomUser user) {
        String sql = """
                insert into %s (USERNAME, NAME, FIRST_NAME, LAST_NAME, PASSWORD_HASH,
                                NAME_VISIBLE, EMAIL, EMAIL_VISIBLE, USER_ENABLED, USER_EXTERNAL, STATUS,
                                FAILED_ATTEMPTS, LAST_FAILED_AT, ACCOUNT_LOCKED_UNTIL, CREATION_DATE, MODIFIED_DATE)
                values (:username, :name, :firstName, :lastName, :password,
                        :nameVisible, :email, :emailVisible, :enabled, :external, :status,
                        :failedAttempts, :lastFailedAt, :accountLockedUntil, :creationDate, :modifiedDate)
                returning USER_ID
                """.formatted(TABLE);
        MapSqlParameterSource params = toParams(user);
        Long id = namedTemplate.queryForObject(sql, params, Long.class);
        user.setUserId(id);
        return user;
    }

    private CustomUser update(CustomUser user) {
        String sql = """
                update %s
                   set NAME = :name,
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
                """.formatted(TABLE);
        namedTemplate.update(sql, toParams(user));
        return user;
    }

    private MapSqlParameterSource toParams(CustomUser u) {
        return new MapSqlParameterSource()
                .addValue("userId", u.getUserId())
                .addValue("username", u.getUsername())
                .addValue("name", u.getName())
                .addValue("firstName", u.getFirstName())
                .addValue("lastName", u.getLastName())
                .addValue("password", u.getPassword())
                .addValue("nameVisible", u.isNameVisible())
                .addValue("email", u.getEmail())
                .addValue("emailVisible", u.isEmailVisible())
                .addValue("enabled", u.isEnabled())
                .addValue("external", u.isExternal())
                .addValue("status", u.getStatus() == null ? null : u.getStatus().ordinal())
                .addValue("failedAttempts", u.getFailedAttempts())
                .addValue("lastFailedAt", u.getLastFailedAt())
                .addValue("accountLockedUntil", u.getAccountLockedUntil())
                .addValue("creationDate", u.getCreationDate())
                .addValue("modifiedDate", u.getModifiedDate());
    }

    private Page<CustomUser> queryPage(String select, String count, Map<String, ?> params, Pageable pageable) {
        long total = namedTemplate.queryForObject(count, params, Long.class);
        if (total == 0) {
            return Page.empty(pageable);
        }
        String ordered = select + buildOrderBy(pageable) + " limit :limit offset :offset";
        Map<String, Object> queryParams = new HashMap<>(params);
        queryParams.put("limit", pageable.getPageSize());
        queryParams.put("offset", pageable.getOffset());
        List<CustomUser> content = namedTemplate.query(ordered, queryParams, USER_ROW_MAPPER);
        return new PageImpl<>(content, pageable, total);
    }

    private Optional<CustomUser> queryOptional(String sql, Map<String, ?> params) {
        List<CustomUser> list = namedTemplate.query(sql, params, USER_ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private <T> Optional<T> queryOptional(String sql, Map<String, ?> params, RowMapper<T> mapper) {
        List<T> list = namedTemplate.query(sql, params, mapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        return "%" + keyword.toLowerCase() + "%";
    }

    private String buildOrderBy(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" order by ");
        boolean first = true;
        for (var order : pageable.getSort()) {
            if (!first) sb.append(", ");
            first = false;
            String column = SORT_COLUMNS.getOrDefault(order.getProperty(), "USER_ID");
            sb.append(column).append(order.isAscending() ? " asc" : " desc");
        }
        return sb.toString();
    }
}
