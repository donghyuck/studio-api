package studio.one.base.security.authentication.lock;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountLockJdbcRepository implements AccountLockRepository {

    private final NamedParameterJdbcTemplate template;

    public AccountLockJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public int bumpFailedAttempts(String username, Instant now) {
        String sql = """
                update TB_APPLICATION_USER
                   set FAILED_ATTEMPTS = FAILED_ATTEMPTS + 1,
                       LAST_FAILED_AT = :now
                 where USERNAME = :username
                """;
        return template.update(sql, Map.of(
                "now", Timestamp.from(now),
                "username", username));
    }

    @Override
    public int lockUntil(String username, Instant until) {
        String sql = """
                update TB_APPLICATION_USER
                   set ACCOUNT_LOCKED_UNTIL = :until
                 where USERNAME = :username
                """;
        Timestamp ts = until == null ? null : Timestamp.from(until);
        return template.update(sql, Map.of("until", ts, "username", username));
    }

    @Override
    public int resetLockState(String username) {
        String sql = """
                update TB_APPLICATION_USER
                   set FAILED_ATTEMPTS    = 0,
                       LAST_FAILED_AT     = null,
                       ACCOUNT_LOCKED_UNTIL = null
                 where USERNAME = :username
                """;
        return template.update(sql, Map.of("username", username));
    }

    @Override
    public Integer findFailedAttempts(String username) {
        String sql = "select FAILED_ATTEMPTS from TB_APPLICATION_USER where USERNAME = :username";
        try {
            return template.queryForObject(sql, Map.of("username", username), Integer.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findLastFailedAt(String username) {
        String sql = "select LAST_FAILED_AT from TB_APPLICATION_USER where USERNAME = :username";
        try {
            Timestamp ts = template.queryForObject(sql, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findAccountLockedUntil(String username) {
        String sql = "select ACCOUNT_LOCKED_UNTIL from TB_APPLICATION_USER where USERNAME = :username";
        try {
            Timestamp ts = template.queryForObject(sql, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
