package studio.one.base.user.infrastructure.persistence.jdbc;

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
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompanyMemberKey;
import studio.one.base.user.domain.port.ApplicationCompanyMemberKeyRepository;

@Repository(ApplicationCompanyMemberKeyRepository.SERVICE_NAME)
public class ApplicationCompanyMemberKeyJdbcRepository extends BaseJdbcRepository implements ApplicationCompanyMemberKeyRepository {

    private static final RowMapper<ApplicationCompanyMemberKey> KEY_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationCompanyMemberKey key = new ApplicationCompanyMemberKey();
        key.setKeyId(rs.getLong("KEY_ID"));
        key.setCompanyId(rs.getLong("COMPANY_ID"));
        key.setRole(CompanyRole.valueOf(rs.getString("ROLE")));
        key.setKeyHash(rs.getString("KEY_HASH"));
        key.setStatus(CompanyMemberKeyStatus.valueOf(rs.getString("STATUS")));
        Timestamp expiresAt = rs.getTimestamp("EXPIRES_AT");
        key.setExpiresAt(expiresAt == null ? null : expiresAt.toInstant());
        int maxUses = rs.getInt("MAX_USES");
        key.setMaxUses(rs.wasNull() ? null : maxUses);
        key.setUsedCount(rs.getInt("USED_COUNT"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        key.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        key.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
        long createdBy = rs.getLong("CREATED_BY");
        key.setCreatedBy(rs.wasNull() ? null : createdBy);
        long updatedBy = rs.getLong("UPDATED_BY");
        key.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        return key;
    };

    public ApplicationCompanyMemberKeyJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public Optional<ApplicationCompanyMemberKey> findById(Long keyId) {
        String sql = selectSql() + " where KEY_ID = :keyId";
        return queryOptional(sql, Map.of("keyId", keyId), KEY_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationCompanyMemberKey> findByKeyHash(String keyHash) {
        String sql = selectSql() + " where KEY_HASH = :keyHash";
        return queryOptional(sql, Map.of("keyHash", keyHash), KEY_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationCompanyMemberKey> findForUpdateById(Long keyId) {
        String sql = selectSql() + " where KEY_ID = :keyId for update";
        return queryOptional(sql, Map.of("keyId", keyId), KEY_ROW_MAPPER);
    }

    @Override
    public Optional<ApplicationCompanyMemberKey> findForUpdateByKeyHash(String keyHash) {
        String sql = selectSql() + " where KEY_HASH = :keyHash for update";
        return queryOptional(sql, Map.of("keyHash", keyHash), KEY_ROW_MAPPER);
    }

    @Override
    public ApplicationCompanyMemberKey save(ApplicationCompanyMemberKey key) {
        if (key.getKeyId() == null) {
            return insert(key);
        }
        update(key);
        return findById(key.getKeyId()).orElse(key);
    }

    private ApplicationCompanyMemberKey insert(ApplicationCompanyMemberKey key) {
        Instant now = Instant.now();
        if (key.getCreatedAt() == null) {
            key.setCreatedAt(now);
        }
        if (key.getUpdatedAt() == null) {
            key.setUpdatedAt(key.getCreatedAt());
        }
        if (key.getStatus() == null) {
            key.setStatus(CompanyMemberKeyStatus.ACTIVE);
        }
        String sql = (
"insert into TB_APPLICATION_COMPANY_MEMBER_KEY\\n" + "    (COMPANY_ID, ROLE, KEY_HASH, STATUS, EXPIRES_AT, MAX_USES, USED_COUNT, CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY)\\n" + "values\\n" + "    (:companyId, :role, :keyHash, :status, :expiresAt, :maxUses, :usedCount, :createdAt, :createdBy, :updatedAt, :updatedBy)\\n");
        KeyHolder holder = new GeneratedKeyHolder();
        namedTemplate.update(sql, new MapSqlParameterSource(params(key)), holder, new String[] { "key_id" });
        Number generated = holder.getKey();
        if (generated != null) {
            key.setKeyId(generated.longValue());
        }
        return key.getKeyId() == null ? key : findById(key.getKeyId()).orElse(key);
    }

    private void update(ApplicationCompanyMemberKey key) {
        key.setUpdatedAt(Instant.now());
        String sql = (
"update TB_APPLICATION_COMPANY_MEMBER_KEY\\n" + "   set ROLE = :role,\\n" + "       STATUS = :status,\\n" + "       EXPIRES_AT = :expiresAt,\\n" + "       MAX_USES = :maxUses,\\n" + "       USED_COUNT = :usedCount,\\n" + "       UPDATED_AT = :updatedAt,\\n" + "       UPDATED_BY = :updatedBy\\n" + " where KEY_ID = :keyId\\n");
        namedTemplate.update(sql, params(key));
    }

    private Map<String, Object> params(ApplicationCompanyMemberKey key) {
        Map<String, Object> params = new HashMap<>();
        params.put("keyId", key.getKeyId());
        params.put("companyId", key.getCompanyId() != null ? key.getCompanyId() : key.getCompany().getCompanyId());
        params.put("role", key.getRole().name());
        params.put("keyHash", key.getKeyHash());
        params.put("status", key.getStatus().name());
        params.put("expiresAt", key.getExpiresAt() == null ? null : Timestamp.from(key.getExpiresAt()));
        params.put("maxUses", key.getMaxUses());
        params.put("usedCount", key.getUsedCount());
        params.put("createdAt", Timestamp.from(key.getCreatedAt()));
        params.put("createdBy", key.getCreatedBy());
        params.put("updatedAt", Timestamp.from(key.getUpdatedAt()));
        params.put("updatedBy", key.getUpdatedBy());
        return params;
    }

    private String selectSql() {
        return (
"select KEY_ID, COMPANY_ID, ROLE, KEY_HASH, STATUS, EXPIRES_AT, MAX_USES, USED_COUNT,\\n" + "       CREATED_AT, CREATED_BY, UPDATED_AT, UPDATED_BY\\n" + "  from TB_APPLICATION_COMPANY_MEMBER_KEY\\n");
    }
}
