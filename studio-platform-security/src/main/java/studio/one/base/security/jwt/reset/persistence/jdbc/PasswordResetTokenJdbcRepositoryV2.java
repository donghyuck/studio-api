package studio.one.base.security.jwt.reset.persistence.jdbc;

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

import studio.one.base.security.jwt.reset.domain.PasswordResetToken;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;

/**
 * JDBC implementation backed by sqlset-stored statements.
 */
public class PasswordResetTokenJdbcRepositoryV2 implements PasswordResetTokenRepository {

    private static final RowMapper<PasswordResetToken> ROW_MAPPER = (rs, rowNum) -> PasswordResetToken.builder()
            .id(rs.getLong("ID"))
            .userId(rs.getLong("USER_ID"))
            .token(rs.getString("TOKEN"))
            .expiresAt(rs.getTimestamp("EXPIRES_AT").toInstant())
            .used(rs.getBoolean("USED"))
            .createdAt(rs.getTimestamp("CREATED_AT").toInstant())
            .build();

    @SqlStatement("security.passwordResetToken.insert")
    private String insertSql;

    @SqlStatement("security.passwordResetToken.update")
    private String updateSql;

    @SqlStatement("security.passwordResetToken.findByToken")
    private String findByTokenSql;

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
    public Optional<PasswordResetToken> findByToken(String token) {
        return template.query(findByTokenSql, Map.of("token", token), ROW_MAPPER).stream().findFirst();
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
        template.update(insertSql, params, keyHolder, new String[] { "ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            token.setId(key.longValue());
        }
        return token;
    }

    private PasswordResetToken update(PasswordResetToken token) {
        String sql = updateSql;
        Map<String, Object> params = new HashMap<>();
        params.put("userId", token.getUserId());
        params.put("token", token.getToken());
        params.put("expiresAt", Timestamp.from(token.getExpiresAt()));
        params.put("used", token.isUsed());
        params.put("id", token.getId());
        template.update(sql, params);
        return token;
    }
}
