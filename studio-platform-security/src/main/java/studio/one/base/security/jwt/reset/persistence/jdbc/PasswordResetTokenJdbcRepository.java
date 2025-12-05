package studio.one.base.security.jwt.reset.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import studio.one.base.security.jwt.reset.domain.PasswordResetToken;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;

/**
 * JDBC implementation of {@link PasswordResetTokenRepository}.
 */
public class PasswordResetTokenJdbcRepository implements PasswordResetTokenRepository {

    private static final String TABLE = "TB_APPLICATION_PASSWORD_RESET_TOKEN";

    private static final RowMapper<PasswordResetToken> ROW_MAPPER = (rs, rowNum) -> PasswordResetToken.builder()
            .id(rs.getLong("ID"))
            .userId(rs.getLong("USER_ID"))
            .token(rs.getString("TOKEN"))
            .expiresAt(rs.getTimestamp("EXPIRES_AT").toInstant())
            .used(rs.getBoolean("USED"))
            .createdAt(optionalInstant(rs, "CREATED_AT"))
            .build();

    private final NamedParameterJdbcTemplate template;
    private final SimpleJdbcInsert insert;

    public PasswordResetTokenJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
        this.insert = new SimpleJdbcInsert(template.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("ID");
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
        String sql = """
                select ID, USER_ID, TOKEN, EXPIRES_AT, USED, CREATED_AT
                  from %s
                 where TOKEN = :token
                """.formatted(TABLE);
        return template.query(sql, Map.of("token", token), ROW_MAPPER).stream().findFirst();
    }

    private PasswordResetToken insert(PasswordResetToken token) {
        Instant now = token.getCreatedAt() == null ? Instant.now() : token.getCreatedAt();
        token.setCreatedAt(now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("USER_ID", token.getUserId())
                .addValue("TOKEN", token.getToken())
                .addValue("EXPIRES_AT", Timestamp.from(token.getExpiresAt()))
                .addValue("USED", token.isUsed())
                .addValue("CREATED_AT", Timestamp.from(now));
        Number key = insert.executeAndReturnKey(params);
        token.setId(key.longValue());
        return token;
    }

    private PasswordResetToken update(PasswordResetToken token) {
        String sql = """
                update %s
                   set USER_ID = :userId,
                       TOKEN = :token,
                       EXPIRES_AT = :expiresAt,
                       USED = :used
                 where ID = :id
                """.formatted(TABLE);
        Map<String, Object> params = new HashMap<>();
        params.put("userId", token.getUserId());
        params.put("token", token.getToken());
        params.put("expiresAt", Timestamp.from(token.getExpiresAt()));
        params.put("used", token.isUsed());
        params.put("id", token.getId());
        template.update(sql, params);
        return token;
    }

    private static Instant optionalInstant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
