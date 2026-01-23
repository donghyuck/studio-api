package studio.one.platform.objecttype.db.jpa;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypeRow;
import studio.one.platform.objecttype.db.jpa.entity.ObjectTypeEntity;
import studio.one.platform.objecttype.db.jpa.entity.ObjectTypePolicyEntity;
import studio.one.platform.objecttype.db.jpa.repo.ObjectTypeJpaRepository;
import studio.one.platform.objecttype.db.jpa.repo.ObjectTypePolicyJpaRepository;

public class JpaObjectTypeStore implements ObjectTypeStore {

    private final ObjectTypeJpaRepository typeRepository;
    private final ObjectTypePolicyJpaRepository policyRepository;
    private final EntityManager entityManager;

    public JpaObjectTypeStore(ObjectTypeJpaRepository typeRepository,
            ObjectTypePolicyJpaRepository policyRepository,
            EntityManager entityManager) {
        this.typeRepository = typeRepository;
        this.policyRepository = policyRepository;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ObjectTypeRow> findByType(int objectType) {
        return typeRepository.findById(objectType).map(JpaObjectTypeStore::toRow);
    }

    @Override
    public Optional<ObjectTypeRow> findByCode(String code) {
        return typeRepository.findByCode(code).map(JpaObjectTypeStore::toRow);
    }

    @Override
    public Page<ObjectTypeRow> search(String domain, String status, String q, Pageable pageable) {
        StringBuilder jpql = new StringBuilder("select t from ObjectTypeEntity t where 1=1");
        StringBuilder countJpql = new StringBuilder("select count(t) from ObjectTypeEntity t where 1=1");
        Map<String, Object> params = new HashMap<>();
        if (StringUtils.hasText(domain)) {
            jpql.append(" and t.domain = :domain");
            countJpql.append(" and t.domain = :domain");
            params.put("domain", domain);
        }
        if (StringUtils.hasText(status)) {
            jpql.append(" and t.status = :status");
            countJpql.append(" and t.status = :status");
            params.put("status", status);
        }
        if (StringUtils.hasText(q)) {
            jpql.append(" and (lower(t.code) like :q or lower(t.name) like :q)");
            countJpql.append(" and (lower(t.code) like :q or lower(t.name) like :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        jpql.append(" order by t.objectType asc");
        TypedQuery<ObjectTypeEntity> query = entityManager.createQuery(jpql.toString(), ObjectTypeEntity.class);
        params.forEach(query::setParameter);
        if (pageable != null && pageable.isPaged()) {
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize());
        }
        List<ObjectTypeEntity> content = query.getResultList();
        if (pageable == null || pageable.isUnpaged()) {
            List<ObjectTypeRow> rows = content.stream().map(JpaObjectTypeStore::toRow).toList();
            return new PageImpl<>(rows, pageable == null ? Pageable.unpaged() : pageable, rows.size());
        }
        var countQuery = entityManager.createQuery(countJpql.toString(), Long.class);
        params.forEach(countQuery::setParameter);
        Long total = countQuery.getSingleResult();
        List<ObjectTypeRow> rows = content.stream().map(JpaObjectTypeStore::toRow).toList();
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public ObjectTypeRow upsert(ObjectTypeRow row) {
        String sql = """
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
        var q = entityManager.createNativeQuery(sql);
        applyTypeParams(q, row);
        q.executeUpdate();
        return findByType(row.getObjectType()).orElse(row);
    }

    @Override
    public ObjectTypeRow patch(int objectType, ObjectTypeRow patch) {
        StringBuilder sql = new StringBuilder("update tb_application_object_type set ");
        boolean any = false;
        if (StringUtils.hasText(patch.getCode())) {
            sql.append("code = :code, ");
            any = true;
        }
        if (StringUtils.hasText(patch.getName())) {
            sql.append("name = :name, ");
            any = true;
        }
        if (StringUtils.hasText(patch.getDomain())) {
            sql.append("domain = :domain, ");
            any = true;
        }
        if (StringUtils.hasText(patch.getStatus())) {
            sql.append("status = :status, ");
            any = true;
        }
        if (patch.getDescription() != null) {
            sql.append("description = :description, ");
            any = true;
        }
        if (!any) {
            return findByType(objectType).orElse(null);
        }
        sql.append("updated_by = :updatedBy, updated_by_id = :updatedById, updated_at = :updatedAt ");
        sql.append("where object_type = :objectType");
        var q = entityManager.createNativeQuery(sql.toString());
        q.setParameter("objectType", objectType);
        if (StringUtils.hasText(patch.getCode())) q.setParameter("code", patch.getCode());
        if (StringUtils.hasText(patch.getName())) q.setParameter("name", patch.getName());
        if (StringUtils.hasText(patch.getDomain())) q.setParameter("domain", patch.getDomain());
        if (StringUtils.hasText(patch.getStatus())) q.setParameter("status", patch.getStatus());
        if (patch.getDescription() != null) q.setParameter("description", patch.getDescription());
        q.setParameter("updatedBy", patch.getUpdatedBy());
        q.setParameter("updatedById", patch.getUpdatedById());
        q.setParameter("updatedAt", patch.getUpdatedAt() != null ? patch.getUpdatedAt() : Instant.now());
        q.executeUpdate();
        return findByType(objectType).orElse(null);
    }

    @Override
    public Optional<ObjectTypePolicyRow> findPolicy(int objectType) {
        return policyRepository.findById(objectType).map(JpaObjectTypeStore::toPolicyRow);
    }

    @Override
    public ObjectTypePolicyRow upsertPolicy(ObjectTypePolicyRow row) {
        String sql = """
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
        var q = entityManager.createNativeQuery(sql);
        applyPolicyParams(q, row);
        q.executeUpdate();
        return findPolicy(row.getObjectType()).orElse(row);
    }

    private static ObjectTypeRow toRow(ObjectTypeEntity e) {
        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(e.getObjectType());
        row.setCode(e.getCode());
        row.setName(e.getName());
        row.setDomain(e.getDomain());
        row.setStatus(e.getStatus());
        row.setDescription(e.getDescription());
        row.setCreatedBy(e.getCreatedBy());
        row.setCreatedById(e.getCreatedById());
        row.setCreatedAt(e.getCreatedAt());
        row.setUpdatedBy(e.getUpdatedBy());
        row.setUpdatedById(e.getUpdatedById());
        row.setUpdatedAt(e.getUpdatedAt());
        return row;
    }

    private static ObjectTypePolicyRow toPolicyRow(ObjectTypePolicyEntity e) {
        ObjectTypePolicyRow row = new ObjectTypePolicyRow();
        row.setObjectType(e.getObjectType());
        row.setMaxFileMb(e.getMaxFileMb());
        row.setAllowedExt(e.getAllowedExt());
        row.setAllowedMime(e.getAllowedMime());
        row.setPolicyJson(e.getPolicyJson());
        row.setCreatedBy(e.getCreatedBy());
        row.setCreatedById(e.getCreatedById());
        row.setCreatedAt(e.getCreatedAt());
        row.setUpdatedBy(e.getUpdatedBy());
        row.setUpdatedById(e.getUpdatedById());
        row.setUpdatedAt(e.getUpdatedAt());
        return row;
    }

    private void applyTypeParams(javax.persistence.Query q, ObjectTypeRow row) {
        q.setParameter("objectType", row.getObjectType());
        q.setParameter("code", row.getCode());
        q.setParameter("name", row.getName());
        q.setParameter("domain", row.getDomain());
        q.setParameter("status", row.getStatus());
        q.setParameter("description", row.getDescription());
        q.setParameter("createdBy", row.getCreatedBy());
        q.setParameter("createdById", row.getCreatedById());
        q.setParameter("createdAt", row.getCreatedAt());
        q.setParameter("updatedBy", row.getUpdatedBy());
        q.setParameter("updatedById", row.getUpdatedById());
        q.setParameter("updatedAt", row.getUpdatedAt());
    }

    private void applyPolicyParams(javax.persistence.Query q, ObjectTypePolicyRow row) {
        q.setParameter("objectType", row.getObjectType());
        q.setParameter("maxFileMb", row.getMaxFileMb());
        q.setParameter("allowedExt", row.getAllowedExt());
        q.setParameter("allowedMime", row.getAllowedMime());
        q.setParameter("policyJson", row.getPolicyJson());
        q.setParameter("createdBy", row.getCreatedBy());
        q.setParameter("createdById", row.getCreatedById());
        q.setParameter("createdAt", row.getCreatedAt());
        q.setParameter("updatedBy", row.getUpdatedBy());
        q.setParameter("updatedById", row.getUpdatedById());
        q.setParameter("updatedAt", row.getUpdatedAt());
    }
}
