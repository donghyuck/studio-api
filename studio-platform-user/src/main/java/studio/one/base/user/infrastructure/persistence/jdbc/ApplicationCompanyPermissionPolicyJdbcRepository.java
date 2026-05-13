package studio.one.base.user.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicyId;
import studio.one.base.user.domain.port.ApplicationCompanyPermissionPolicyRepository;

@Repository(ApplicationCompanyPermissionPolicyRepository.SERVICE_NAME)
public class ApplicationCompanyPermissionPolicyJdbcRepository extends BaseJdbcRepository
        implements ApplicationCompanyPermissionPolicyRepository {

    private static final RowMapper<ApplicationCompanyPermissionPolicy> POLICY_ROW_MAPPER = (rs, rowNum) -> {
        ApplicationCompanyPermissionPolicy policy = new ApplicationCompanyPermissionPolicy();
        policy.setId(new ApplicationCompanyPermissionPolicyId(
                rs.getLong("COMPANY_ID"),
                CompanyRole.valueOf(rs.getString("ROLE")),
                rs.getString("ACTION_NAME")));
        policy.setEnabled(rs.getBoolean("ENABLED"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        policy.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        policy.setUpdatedAt(updatedAt == null ? null : updatedAt.toInstant());
        long updatedBy = rs.getLong("UPDATED_BY");
        policy.setUpdatedBy(rs.wasNull() ? null : updatedBy);
        return policy;
    };

    public ApplicationCompanyPermissionPolicyJdbcRepository(NamedParameterJdbcTemplate namedTemplate) {
        super(namedTemplate);
    }

    @Override
    public List<ApplicationCompanyPermissionPolicy> findAllByCompanyId(Long companyId) {
        String sql = (
"select COMPANY_ID, ROLE, ACTION_NAME, ENABLED, CREATED_AT, UPDATED_AT, UPDATED_BY\\n" + "  from TB_APPLICATION_COMPANY_PERMISSION_POLICY\\n" + " where COMPANY_ID = :companyId\\n" + " order by ROLE, ACTION_NAME\\n");
        return namedTemplate.query(sql, Map.of("companyId", companyId), POLICY_ROW_MAPPER);
    }

    @Override
    public void deleteAllByCompanyId(Long companyId) {
        namedTemplate.update("delete from TB_APPLICATION_COMPANY_PERMISSION_POLICY\\n" + " where COMPANY_ID = :companyId\\n", Map.of("companyId", companyId));
    }

    @Override
    public ApplicationCompanyPermissionPolicy save(ApplicationCompanyPermissionPolicy policy) {
        Instant now = Instant.now();
        if (policy.getCreatedAt() == null) {
            policy.setCreatedAt(now);
        }
        if (policy.getUpdatedAt() == null) {
            policy.setUpdatedAt(policy.getCreatedAt());
        }
        String sql = (
"insert into TB_APPLICATION_COMPANY_PERMISSION_POLICY\\n" + "    (COMPANY_ID, ROLE, ACTION_NAME, ENABLED, CREATED_AT, UPDATED_AT, UPDATED_BY)\\n" + "values\\n" + "    (:companyId, :role, :action, :enabled, :createdAt, :updatedAt, :updatedBy)\\n");
        Map<String, Object> params = new HashMap<>();
        params.put("companyId", policy.getId().getCompanyId());
        params.put("role", policy.getId().getRole().name());
        params.put("action", policy.getId().getAction());
        params.put("enabled", policy.isEnabled());
        params.put("createdAt", Timestamp.from(policy.getCreatedAt()));
        params.put("updatedAt", Timestamp.from(policy.getUpdatedAt()));
        params.put("updatedBy", policy.getUpdatedBy());
        namedTemplate.update(sql, params);
        return policy;
    }
}
