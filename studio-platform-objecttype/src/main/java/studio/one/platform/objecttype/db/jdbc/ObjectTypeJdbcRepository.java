package studio.one.platform.objecttype.db.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypeRow;

public class ObjectTypeJdbcRepository implements ObjectTypeStore {

    private static final RowMapper<ObjectTypeRow> OBJECT_TYPE_ROW_MAPPER = (rs, rowNum) -> {
        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(rs.getInt("objectType"));
        row.setCode(rs.getString("code"));
        row.setName(rs.getString("name"));
        row.setDomain(rs.getString("domain"));
        row.setStatus(rs.getString("status"));
        row.setDescription(rs.getString("description"));
        row.setCreatedBy(rs.getString("createdBy"));
        row.setCreatedById(rs.getLong("createdById"));
        Timestamp createdAt = rs.getTimestamp("createdAt");
        row.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        row.setUpdatedBy(rs.getString("updatedBy"));
        row.setUpdatedById(rs.getLong("updatedById"));
        Timestamp updatedAt = rs.getTimestamp("updatedAt");
        row.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return row;
    };

    private static final RowMapper<ObjectTypePolicyRow> POLICY_ROW_MAPPER = (rs, rowNum) -> {
        ObjectTypePolicyRow row = new ObjectTypePolicyRow();
        row.setObjectType(rs.getInt("objectType"));
        int maxFileMb = rs.getInt("maxFileMb");
        row.setMaxFileMb(rs.wasNull() ? null : maxFileMb);
        row.setAllowedExt(rs.getString("allowedExt"));
        row.setAllowedMime(rs.getString("allowedMime"));
        row.setPolicyJson(rs.getString("policyJson"));
        row.setCreatedBy(rs.getString("createdBy"));
        row.setCreatedById(rs.getLong("createdById"));
        Timestamp createdAt = rs.getTimestamp("createdAt");
        row.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        row.setUpdatedBy(rs.getString("updatedBy"));
        row.setUpdatedById(rs.getLong("updatedById"));
        Timestamp updatedAt = rs.getTimestamp("updatedAt");
        row.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        return row;
    };

    @SqlStatement("objecttype.selectByType")
    private String selectByTypeSql;

    @SqlStatement("objecttype.selectByCode")
    private String selectByCodeSql;

    @SqlStatement("objecttype.selectPolicyByType")
    private String selectPolicyByTypeSql;

    @SqlStatement("objecttype.searchBase")
    private String searchBaseSql;

    @SqlStatement("objecttype.countBase")
    private String countBaseSql;

    @SqlStatement("objecttype.upsertType")
    private String upsertTypeSql;

    @SqlStatement("objecttype.upsertPolicy")
    private String upsertPolicySql;

    private final NamedParameterJdbcTemplate template;

    public ObjectTypeJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public Optional<ObjectTypeRow> findByType(int objectType) {
        return template.query(selectByTypeSql, Map.of("objectType", objectType), OBJECT_TYPE_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ObjectTypeRow> findByCode(String code) {
        return template.query(selectByCodeSql, Map.of("code", code), OBJECT_TYPE_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ObjectTypePolicyRow> findPolicy(int objectType) {
        return template.query(selectPolicyByTypeSql, Map.of("objectType", objectType), POLICY_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Page<ObjectTypeRow> search(String domain, String status, String q, Pageable pageable) {
        Pageable page = pageable != null ? pageable : Pageable.unpaged();
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(domain, status, q, params);
        if (page.isUnpaged()) {
            String dataSql = searchBaseSql + where + " order by object_type asc";
            List<ObjectTypeRow> rows = template.query(dataSql, params, OBJECT_TYPE_ROW_MAPPER);
            return new PageImpl<>(rows, page, rows.size());
        }
        String countSql = countBaseSql + where;
        long total = template.queryForObject(countSql, params, Long.class);
        if (total == 0) {
            return Page.empty(page);
        }
        String dataSql = searchBaseSql + where + " order by object_type asc";
        if (page.isPaged()) {
            params.put("limit", page.getPageSize());
            params.put("offset", page.getOffset());
            dataSql += " limit :limit offset :offset";
        }
        List<ObjectTypeRow> rows = template.query(dataSql, params, OBJECT_TYPE_ROW_MAPPER);
        return new PageImpl<>(rows, page, total);
    }

    @Override
    public ObjectTypeRow upsert(ObjectTypeRow row) {
        Map<String, Object> params = typeParams(row, true);
        template.update(upsertTypeSql, params);
        return findByType(row.getObjectType()).orElse(row);
    }

    @Override
    public ObjectTypeRow patch(int objectType, ObjectTypeRow patch) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder("update tb_application_object_type set ");
        boolean any = false;
        if (StringUtils.hasText(patch.getCode())) {
            sql.append("code = :code, ");
            params.put("code", patch.getCode());
            any = true;
        }
        if (StringUtils.hasText(patch.getName())) {
            sql.append("name = :name, ");
            params.put("name", patch.getName());
            any = true;
        }
        if (StringUtils.hasText(patch.getDomain())) {
            sql.append("domain = :domain, ");
            params.put("domain", patch.getDomain());
            any = true;
        }
        if (StringUtils.hasText(patch.getStatus())) {
            sql.append("status = :status, ");
            params.put("status", patch.getStatus());
            any = true;
        }
        if (patch.getDescription() != null) {
            sql.append("description = :description, ");
            params.put("description", patch.getDescription());
            any = true;
        }
        if (!any) {
            return findByType(objectType).orElse(null);
        }
        sql.append("updated_by = :updatedBy, updated_by_id = :updatedById, updated_at = :updatedAt ");
        params.put("updatedBy", patch.getUpdatedBy());
        params.put("updatedById", patch.getUpdatedById());
        params.put("updatedAt", Timestamp.from(Optional.ofNullable(patch.getUpdatedAt()).orElseGet(Instant::now)));
        sql.append("where object_type = :objectType");
        params.put("objectType", objectType);
        template.update(sql.toString(), params);
        return findByType(objectType).orElse(null);
    }

    @Override
    public ObjectTypePolicyRow upsertPolicy(ObjectTypePolicyRow row) {
        Map<String, Object> params = policyParams(row, true);
        template.update(upsertPolicySql, params);
        return findPolicy(row.getObjectType()).orElse(row);
    }

    private String buildWhere(String domain, String status, String q, Map<String, Object> params) {
        StringBuilder where = new StringBuilder(" where 1=1");
        if (StringUtils.hasText(domain)) {
            where.append(" and domain = :domain");
            params.put("domain", domain);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and status = :status");
            params.put("status", status);
        }
        if (StringUtils.hasText(q)) {
            where.append(" and (lower(code) like :q or lower(name) like :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        return where.toString();
    }

    private Map<String, Object> typeParams(ObjectTypeRow row, boolean includeCreate) {
        Map<String, Object> params = new HashMap<>();
        params.put("objectType", row.getObjectType());
        params.put("code", row.getCode());
        params.put("name", row.getName());
        params.put("domain", row.getDomain());
        params.put("status", row.getStatus());
        params.put("description", row.getDescription());
        params.put("createdBy", row.getCreatedBy());
        params.put("createdById", row.getCreatedById());
        params.put("createdAt", ts(row.getCreatedAt()));
        params.put("updatedBy", row.getUpdatedBy());
        params.put("updatedById", row.getUpdatedById());
        params.put("updatedAt", ts(row.getUpdatedAt()));
        return params;
    }

    private Map<String, Object> policyParams(ObjectTypePolicyRow row, boolean includeCreate) {
        Map<String, Object> params = new HashMap<>();
        params.put("objectType", row.getObjectType());
        params.put("maxFileMb", row.getMaxFileMb());
        params.put("allowedExt", row.getAllowedExt());
        params.put("allowedMime", row.getAllowedMime());
        params.put("policyJson", row.getPolicyJson());
        params.put("createdBy", row.getCreatedBy());
        params.put("createdById", row.getCreatedById());
        params.put("createdAt", ts(row.getCreatedAt()));
        params.put("updatedBy", row.getUpdatedBy());
        params.put("updatedById", row.getUpdatedById());
        params.put("updatedAt", ts(row.getUpdatedAt()));
        return params;
    }

    private Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
