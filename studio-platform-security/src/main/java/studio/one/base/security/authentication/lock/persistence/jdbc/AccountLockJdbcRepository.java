package studio.one.base.security.authentication.lock.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.base.security.authentication.lock.persistence.AccountLockRepository;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;

@Repository
public class AccountLockJdbcRepository implements AccountLockRepository {

    private final NamedParameterJdbcTemplate template;

    @SqlStatement("security.accountLockBumpFailedAttempts")
    private String bumpFailedAttemptsSql;

    @SqlStatement("security.accountLockLockUntil")
    private String lockUntilSql;

    @SqlStatement("security.accountLockResetLockState")
    private String resetLockStateSql;

    @SqlStatement("security.accountLockFindFailedAttempts")
    private String findFailedAttemptsSql;

    @SqlStatement("security.accountLockFindLastFailedAt")
    private String findLastFailedAtSql;

    @SqlStatement("security.accountLockFindAccountLockedUntil")
    private String findAccountLockedUntilSql;

    public AccountLockJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public int bumpFailedAttempts(String username, Instant now) {
        return template.update(bumpFailedAttemptsSql, Map.of(
                "now", Timestamp.from(now),
                "username", username));
    }

    @Override
    public int lockUntil(String username, Instant until) {
        Timestamp ts = until == null ? null : Timestamp.from(until);
        return template.update(lockUntilSql, Map.of("until", ts, "username", username));
    }

    @Override
    public int resetLockState(String username) {
        return template.update(resetLockStateSql, Map.of("username", username));
    }

    @Override
    public Integer findFailedAttempts(String username) {
        try {
            return template.queryForObject(findFailedAttemptsSql, Map.of("username", username), Integer.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findLastFailedAt(String username) {
        try {
            Timestamp ts = template.queryForObject(findLastFailedAtSql, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    public Instant findAccountLockedUntil(String username) {
        try {
            Timestamp ts = template.queryForObject(findAccountLockedUntilSql, Map.of("username", username), Timestamp.class);
            return ts == null ? null : ts.toInstant();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }
}