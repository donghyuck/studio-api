package studio.one.base.user.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;

import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.model.Status;

final class JdbcUserMapper {

    private JdbcUserMapper() {
    }

    static ApplicationUser mapBasicUser(ResultSet rs, int rowNum) throws SQLException {
        ApplicationUser user = new ApplicationUser();
        user.setUserId(rs.getLong("USER_ID"));
        user.setUsername(rs.getString("USERNAME"));
        user.setName(rs.getString("NAME"));
        user.setFirstName(rs.getString("FIRST_NAME"));
        user.setLastName(rs.getString("LAST_NAME"));
        user.setPassword(rs.getString("PASSWORD_HASH"));
        user.setNameVisible(rs.getBoolean("NAME_VISIBLE"));
        user.setEmail(rs.getString("EMAIL"));
        user.setEmailVisible(rs.getBoolean("EMAIL_VISIBLE"));
        user.setEnabled(rs.getBoolean("USER_ENABLED"));
        user.setExternal(rs.getBoolean("USER_EXTERNAL"));

        int statusOrdinal = rs.getInt("STATUS");
        if (!rs.wasNull()) {
            Status[] values = Status.values();
            if (statusOrdinal >= 0 && statusOrdinal < values.length) {
                user.setStatus(values[statusOrdinal]);
            }
        }

        user.setFailedAttempts(rs.getInt("FAILED_ATTEMPTS"));

        Timestamp lastFailed = rs.getTimestamp("LAST_FAILED_AT");
        user.setLastFailedAt(lastFailed == null ? null : lastFailed.toInstant());

        Timestamp lockedUntil = rs.getTimestamp("ACCOUNT_LOCKED_UNTIL");
        user.setAccountLockedUntil(lockedUntil == null ? null : lockedUntil.toInstant());

        Timestamp created = rs.getTimestamp("CREATION_DATE");
        Timestamp modified = rs.getTimestamp("MODIFIED_DATE");
        user.setCreationDate(created == null ? null : created.toInstant());
        user.setModifiedDate(modified == null ? null : modified.toInstant());

        if (user.getCreationDate() == null) {
            user.setCreationDate(Instant.now());
        }
        if (user.getModifiedDate() == null) {
            user.setModifiedDate(user.getCreationDate());
        }

        user.setProperties(new HashMap<>());
        user.setMemberships(new HashSet<>());
        user.setUserRoles(new HashSet<>());
        return user;
    }
}
