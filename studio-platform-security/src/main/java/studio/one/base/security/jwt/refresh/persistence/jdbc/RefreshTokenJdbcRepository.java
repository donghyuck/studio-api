package studio.one.base.security.jwt.refresh.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.lang.Nullable;

import studio.one.base.security.jwt.refresh.domain.entity.RefreshToken;
import studio.one.base.security.jwt.refresh.persistence.RefreshTokenRepository;

/**
 * JDBC implementation of {@link RefreshTokenRepository}.
 */
public class RefreshTokenJdbcRepository implements RefreshTokenRepository {

    private static final String TABLE = "TB_APPLICATIONI_REFRESH_TOKEN";

    private static final RowMapper<RefreshToken> ROW_MAPPER = (rs, rowNum) -> RefreshToken.builder()
            .id(rs.getLong("ID"))
            .userId(rs.getLong("USER_ID"))
            .selector(rs.getString("SELECTOR"))
            .verifierHash(rs.getString("VERIFIER_HASH"))
            .expiresAt(rs.getTimestamp("EXPIRES_AT").toInstant())
            .revoked(rs.getBoolean("REVOKED"))
            .createdAt(optionalInstant(rs, "CREATED_AT"))
            .replacedBy(mapReplacedBy(rs.getObject("REPLACED_BY_ID", Long.class)))
            .build();

    private final NamedParameterJdbcTemplate namedTemplate;
    private final SimpleJdbcInsert insert;

    public RefreshTokenJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        this.namedTemplate = namedTemplate;
        this.insert = new SimpleJdbcInsert(namedTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("ID");
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        if (token.getId() == null) {
            return insert(token);
        }
        return update(token);
    }

    @Override
    public Optional<RefreshToken> findBySelector(String selector) {
        String sql = """
                select ID, USER_ID, SELECTOR, VERIFIER_HASH,
                       EXPIRES_AT, REVOKED, CREATED_AT, REPLACED_BY_ID
                  from %s
                 where SELECTOR = :selector
                """.formatted(TABLE);
        return namedTemplate.query(sql, Map.of("selector", selector), ROW_MAPPER).stream().findFirst();
    }

    private RefreshToken insert(RefreshToken token) {
        Instant now = token.getCreatedAt() == null ? Instant.now() : token.getCreatedAt();
        token.setCreatedAt(now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("USER_ID", token.getUserId())
                .addValue("SELECTOR", token.getSelector())
                .addValue("VERIFIER_HASH", token.getVerifierHash())
                .addValue("EXPIRES_AT", Timestamp.from(token.getExpiresAt()))
                .addValue("REVOKED", token.isRevoked())
                .addValue("CREATED_AT", Timestamp.from(now))
                .addValue("REPLACED_BY_ID", token.getReplacedBy() != null ? token.getReplacedBy().getId() : null);
        Number key = insert.executeAndReturnKey(params);
        token.setId(key.longValue());
        return token;
    }

    private RefreshToken update(RefreshToken token) {
        String sql = """
                update %s
                   set USER_ID = :userId,
                       SELECTOR = :selector,
                       VERIFIER_HASH = :verifierHash,
                       EXPIRES_AT = :expiresAt,
                       REVOKED = :revoked,
                       REPLACED_BY_ID = :replacedById
                 where ID = :id
                """.formatted(TABLE);
        Map<String, Object> params = new HashMap<>();
        params.put("userId", token.getUserId());
        params.put("selector", token.getSelector());
        params.put("verifierHash", token.getVerifierHash());
        params.put("expiresAt", Timestamp.from(token.getExpiresAt()));
        params.put("revoked", token.isRevoked());
        params.put("replacedById", token.getReplacedBy() != null ? token.getReplacedBy().getId() : null);
        params.put("id", token.getId());
        namedTemplate.update(sql, params);
        return token;
    }

    private static Instant optionalInstant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    @Nullable
    private static RefreshToken mapReplacedBy(@Nullable Long id) {
        if (id == null) {
            return null;
        }
        return RefreshToken.builder().id(id).build();
    }
}
