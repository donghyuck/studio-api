package studio.one.base.security.jwt.reset.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import studio.one.base.security.jwt.reset.domain.model.PasswordResetToken;
import studio.one.base.security.jwt.reset.domain.port.PasswordResetTokenRepository;

/**
 * JDBC implementation backed by local SQL statements.
 */
public class PasswordResetTokenJdbcRepositoryV2 implements PasswordResetTokenRepository {

    private static final String INSERT_SQL = "insert into TB_APPLICATION_PASSWORD_RESET_TOKEN\n"
                + "    (USER_ID, TOKEN, EXPIRES_AT, USED, CREATED_AT)\n"
                + "values\n"
                + "    (:userId, :token, :expiresAt, :used, :createdAt)\n"
                + "returning ID\n";

    private static final String UPDATE_SQL = "update TB_APPLICATION_PASSWORD_RESET_TOKEN\n"
                + "   set USER_ID = :userId,\n"
                + "       TOKEN = :token,\n"
                + "       EXPIRES_AT = :expiresAt,\n"
                + "       USED = :used\n"
                + " where ID = :id\n";

    private static final String FIND_ACTIVE_BY_USER_ID_SQL = "select ID, USER_ID, TOKEN, EXPIRES_AT, USED, CREATED_AT\n"
                + "  from TB_APPLICATION_PASSWORD_RESET_TOKEN\n"
                + " where USER_ID = :userId\n"
                + "   and USED = false\n"
                + " order by CREATED_AT desc\n"
                + " limit 1\n";

    private static final RowMapper<PasswordResetToken> ROW_MAPPER = (rs, rowNum) -> PasswordResetToken.builder()
            .id(rs.getLong("ID"))
            .userId(rs.getLong("USER_ID"))
            .token(rs.getString("TOKEN"))
            .expiresAt(rs.getTimestamp("EXPIRES_AT").toInstant())
            .used(rs.getBoolean("USED"))
            .createdAt(rs.getTimestamp("CREATED_AT").toInstant())
            .build();

    private final NamedParameterJdbcTemplate template;

    public PasswordResetTokenJdbcRepositoryV2(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        if (token.getId() == null) {
            return insert(token);
        }
        return update(token);
    }

    @Override
    public Optional<PasswordResetToken> findActiveByUserId(Long userId) {
        return template.query(FIND_ACTIVE_BY_USER_ID_SQL, Map.of("userId", userId), ROW_MAPPER).stream().findFirst();
    }

    private PasswordResetToken insert(PasswordResetToken token) {
        Instant now = token.getCreatedAt() == null ? Instant.now() : token.getCreatedAt();
        token.setCreatedAt(now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", token.getUserId())
                .addValue("token", token.getToken())
                .addValue("expiresAt", Timestamp.from(token.getExpiresAt()))
                .addValue("used", token.isUsed())
                .addValue("createdAt", Timestamp.from(now));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(INSERT_SQL, params, keyHolder, new String[] { "ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            token.setId(key.longValue());
        }
        return token;
    }

    private PasswordResetToken update(PasswordResetToken token) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", token.getUserId());
        params.put("token", token.getToken());
        params.put("expiresAt", Timestamp.from(token.getExpiresAt()));
        params.put("used", token.isUsed());
        params.put("id", token.getId());
        template.update(UPDATE_SQL, params);
        return token;
    }
}
