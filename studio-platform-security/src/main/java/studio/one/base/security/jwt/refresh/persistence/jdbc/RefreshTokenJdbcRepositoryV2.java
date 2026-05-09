package studio.one.base.security.jwt.refresh.persistence.jdbc;

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
import org.springframework.lang.Nullable;

import studio.one.base.security.jwt.refresh.domain.entity.RefreshToken;
import studio.one.base.security.jwt.refresh.persistence.RefreshTokenRepository;

/**
 * JDBC implementation backed by local SQL statements.
 */
public class RefreshTokenJdbcRepositoryV2 implements RefreshTokenRepository {

    private static final String INSERT_SQL = """
            insert into TB_APPLICATION_REFRESH_TOKEN
                (USER_ID, SELECTOR, VERIFIER_HASH, EXPIRES_AT, REVOKED, CREATED_AT, REPLACED_BY_ID)
            values
                (:userId, :selector, :verifierHash, :expiresAt, :revoked, :createdAt, :replacedById)
            returning ID
            """;

    private static final String UPDATE_SQL = """
            update TB_APPLICATION_REFRESH_TOKEN
               set USER_ID = :userId,
                   SELECTOR = :selector,
                   VERIFIER_HASH = :verifierHash,
                   EXPIRES_AT = :expiresAt,
                   REVOKED = :revoked,
                   REPLACED_BY_ID = :replacedById
             where ID = :id
            """;

    private static final String FIND_BY_SELECTOR_SQL = """
            select ID, USER_ID, SELECTOR, VERIFIER_HASH,
                   EXPIRES_AT, REVOKED, CREATED_AT, REPLACED_BY_ID
              from TB_APPLICATION_REFRESH_TOKEN
             where SELECTOR = :selector
            """;

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

    private final NamedParameterJdbcTemplate template;

    public RefreshTokenJdbcRepositoryV2(NamedParameterJdbcTemplate template) {
        this.template = template;
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
        return template.query(FIND_BY_SELECTOR_SQL, Map.of("selector", selector), ROW_MAPPER).stream().findFirst();
    }

    private RefreshToken insert(RefreshToken token) {
        Instant now = token.getCreatedAt() == null ? Instant.now() : token.getCreatedAt();
        token.setCreatedAt(now);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", token.getUserId())
                .addValue("selector", token.getSelector())
                .addValue("verifierHash", token.getVerifierHash())
                .addValue("expiresAt", Timestamp.from(token.getExpiresAt()))
                .addValue("revoked", token.isRevoked())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("replacedById", token.getReplacedBy() != null ? token.getReplacedBy().getId() : null);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        template.update(INSERT_SQL, params, keyHolder, new String[] { "ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            token.setId(key.longValue());
        }
        return token;
    }

    private RefreshToken update(RefreshToken token) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", token.getUserId());
        params.put("selector", token.getSelector());
        params.put("verifierHash", token.getVerifierHash());
        params.put("expiresAt", Timestamp.from(token.getExpiresAt()));
        params.put("revoked", token.isRevoked());
        params.put("replacedById", token.getReplacedBy() != null ? token.getReplacedBy().getId() : null);
        params.put("id", token.getId());
        template.update(UPDATE_SQL, params);
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
