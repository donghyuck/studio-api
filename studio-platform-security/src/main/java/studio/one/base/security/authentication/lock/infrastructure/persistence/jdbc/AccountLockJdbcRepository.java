package studio.one.base.security.authentication.lock.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.base.security.authentication.lock.domain.port.AccountLockRepository;

@Repository
public class AccountLockJdbcRepository implements AccountLockRepository {

    private static final String BUMP_FAILED_ATTEMPTS_SQL = "update TB_APPLICATION_USER\n"
                + "   set FAILED_ATTEMPTS = FAILED_ATTEMPTS + 1,\n"
                + "       LAST_FAILED_AT = :now\n"
                + " where USERNAME = :username\n";

    private static final String LOCK_UNTIL_SQL = "update TB_APPLICATION_USER\n"
                + "   set ACCOUNT_LOCKED_UNTIL = :until\n"
                + " where USERNAME = :username\n";

    private static final String RESET_LOCK_STATE_SQL = "update TB_APPLICATION_USER\n"
                + "   set FAILED_ATTEMPTS = 0,\n"
                + "       LAST_FAILED_AT = null,\n"
                + "       ACCOUNT_LOCKED_UNTIL = null\n"
                + " where USERNAME = :username\n";

    private static final String FIND_FAILED_ATTEMPTS_SQL = "select FAILED_ATTEMPTS\n"
                + "  from TB_APPLICATION_USER\n"
                + " where USERNAME = :username\n";

    private static final String FIND_LAST_FAILED_AT_SQL = "select LAST_FAILED_AT\n"
                + "  from TB_APPLICATION_USER\n"
                + " where USERNAME = :username\n";

    private static final String FIND_ACCOUNT_LOCKED_UNTIL_SQL = "select ACCOUNT_LOCKED_UNTIL\n"
                + "  from TB_APPLICATION_USER\n"
                + " where USERNAME = :username\n";

    private final NamedParameterJdbcTemplate template;

    public AccountLockJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public int bumpFailedAttempts(String username, Instant now) {
        return template.update(BUMP_FAILED_ATTEMPTS_SQL, Map.of(
                "now", Timestamp.from(now),
                "username", username));
    }

    @Override
    public int lockUntil(String username, Instant until) {
        Timestamp ts = until == null ? null : Timestamp.from(until);
        return template.update(LOCK_UNTIL_SQL, Map.of("until", ts, "username", username));
    }

    @Override
    public int resetLockState(String username) {
        return template.update(RESET_LOCK_STATE_SQL, Map.of("username", username));
    }

    @Override
    public Integer findFailedAttempts(String username) {
        try {
            return template.queryForObject(FIND_FAILED_ATTEMPTS_SQL, Map.of("username", username), Integer.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findLastFailedAt(String username) {
        try {
            Timestamp ts = template.queryForObject(FIND_LAST_FAILED_AT_SQL, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findAccountLockedUntil(String username) {
        try {
            Timestamp ts = template.queryForObject(FIND_ACCOUNT_LOCKED_UNTIL_SQL, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
