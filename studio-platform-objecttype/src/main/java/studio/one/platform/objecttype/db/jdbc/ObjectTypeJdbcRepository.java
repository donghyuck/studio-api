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

import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.model.ObjectTypeRow;

public class ObjectTypeJdbcRepository implements ObjectTypeStore {

    private static final String SELECT_TYPE_COLUMNS = """
            SELECT
              OBJECT_TYPE AS objectType,
              CODE AS code,
              NAME AS name,
              DOMAIN AS domain,
              STATUS AS status,
              DESCRIPTION AS description,
              CREATED_BY AS createdBy,
              CREATED_BY_ID AS createdById,
              CREATED_AT AS createdAt,
              UPDATED_BY AS updatedBy,
              UPDATED_BY_ID AS updatedById,
              UPDATED_AT AS updatedAt
            FROM tb_application_object_type
            """;

    private static final String SELECT_BY_TYPE_SQL = SELECT_TYPE_COLUMNS
            + "WHERE OBJECT_TYPE = :objectType";

    private static final String SELECT_BY_CODE_SQL = SELECT_TYPE_COLUMNS
            + "WHERE CODE = :code";

    private static final String SEARCH_BASE_SQL = SELECT_TYPE_COLUMNS;

    private static final String COUNT_BASE_SQL = """
            SELECT count(*)
            FROM tb_application_object_type
            """;

    private static final String SELECT_POLICY_BY_TYPE_SQL = """
            SELECT
              OBJECT_TYPE AS objectType,
              MAX_FILE_MB AS maxFileMb,
              ALLOWED_EXT AS allowedExt,
              ALLOWED_MIME AS allowedMime,
              POLICY_JSON AS policyJson,
              CREATED_BY AS createdBy,
              CREATED_BY_ID AS createdById,
              CREATED_AT AS createdAt,
              UPDATED_BY AS updatedBy,
              UPDATED_BY_ID AS updatedById,
              UPDATED_AT AS updatedAt
            FROM tb_application_object_type_policy
            WHERE OBJECT_TYPE = :objectType
            """;

    private static final String UPSERT_TYPE_SQL = """
            INSERT INTO tb_application_object_type (
              OBJECT_TYPE, CODE, NAME, DOMAIN, STATUS, DESCRIPTION,
              CREATED_BY, CREATED_BY_ID, CREATED_AT,
              UPDATED_BY, UPDATED_BY_ID, UPDATED_AT
            ) VALUES (
              :objectType, :code, :name, :domain, :status, :description,
              :createdBy, :createdById, COALESCE(:createdAt, NOW()),
              :updatedBy, :updatedById, COALESCE(:updatedAt, NOW())
            )
            ON CONFLICT (OBJECT_TYPE) DO UPDATE SET
              CODE = EXCLUDED.CODE,
              NAME = EXCLUDED.NAME,
              DOMAIN = EXCLUDED.DOMAIN,
              STATUS = EXCLUDED.STATUS,
              DESCRIPTION = EXCLUDED.DESCRIPTION,
              UPDATED_BY = EXCLUDED.UPDATED_BY,
              UPDATED_BY_ID = EXCLUDED.UPDATED_BY_ID,
              UPDATED_AT = EXCLUDED.UPDATED_AT
            """;

    private static final String UPSERT_POLICY_SQL = """
            INSERT INTO tb_application_object_type_policy (
              OBJECT_TYPE, MAX_FILE_MB, ALLOWED_EXT, ALLOWED_MIME, POLICY_JSON,
              CREATED_BY, CREATED_BY_ID, CREATED_AT,
              UPDATED_BY, UPDATED_BY_ID, UPDATED_AT
            ) VALUES (
              :objectType, :maxFileMb, :allowedExt, :allowedMime, :policyJson,
              :createdBy, :createdById, COALESCE(:createdAt, NOW()),
              :updatedBy, :updatedById, COALESCE(:updatedAt, NOW())
            )
            ON CONFLICT (OBJECT_TYPE) DO UPDATE SET
              MAX_FILE_MB = EXCLUDED.MAX_FILE_MB,
              ALLOWED_EXT = EXCLUDED.ALLOWED_EXT,
              ALLOWED_MIME = EXCLUDED.ALLOWED_MIME,
              POLICY_JSON = EXCLUDED.POLICY_JSON,
              UPDATED_BY = EXCLUDED.UPDATED_BY,
              UPDATED_BY_ID = EXCLUDED.UPDATED_BY_ID,
              UPDATED_AT = EXCLUDED.UPDATED_AT
            """;

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

    private final NamedParameterJdbcTemplate template;

    public ObjectTypeJdbcRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public Optional<ObjectTypeRow> findByType(int objectType) {
        return template.query(SELECT_BY_TYPE_SQL, Map.of("objectType", objectType), OBJECT_TYPE_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ObjectTypeRow> findByCode(String code) {
        return template.query(SELECT_BY_CODE_SQL, Map.of("code", code), OBJECT_TYPE_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ObjectTypePolicyRow> findPolicy(int objectType) {
        return template.query(SELECT_POLICY_BY_TYPE_SQL, Map.of("objectType", objectType), POLICY_ROW_MAPPER)
                .stream()
                .findFirst();
    }

    @Override
    public Page<ObjectTypeRow> search(String domain, String status, String q, Pageable pageable) {
        Pageable page = pageable != null ? pageable : Pageable.unpaged();
        Map<String, Object> params = new HashMap<>();
        String where = buildWhere(domain, status, q, params);
        if (page.isUnpaged()) {
            String dataSql = SEARCH_BASE_SQL + where + " order by object_type asc";
            List<ObjectTypeRow> rows = template.query(dataSql, params, OBJECT_TYPE_ROW_MAPPER);
            return new PageImpl<>(rows, page, rows.size());
        }
        String countSql = COUNT_BASE_SQL + where;
        long total = template.queryForObject(countSql, params, Long.class);
        if (total == 0) {
            return Page.empty(page);
        }
        String dataSql = SEARCH_BASE_SQL + where + " order by object_type asc";
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
        Map<String, Object> params = typeParams(row);
        template.update(UPSERT_TYPE_SQL, params);
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
        Map<String, Object> params = policyParams(row);
        template.update(UPSERT_POLICY_SQL, params);
        return findPolicy(row.getObjectType()).orElse(row);
    }

    @Override
    public void delete(int objectType) {
        template.update(
                "delete from tb_application_object_type where object_type = :objectType",
                Map.of("objectType", objectType));
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

    private Map<String, Object> typeParams(ObjectTypeRow row) {
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

    private Map<String, Object> policyParams(ObjectTypePolicyRow row) {
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
