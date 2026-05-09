package studio.one.platform.objecttype.infrastructure.persistence.mybatis;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import studio.one.platform.objecttype.domain.port.ObjectTypeStore;
import studio.one.platform.objecttype.infrastructure.persistence.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.infrastructure.persistence.model.ObjectTypeRow;

public class ObjectTypeMyBatisStore implements ObjectTypeStore {

    private final ObjectTypeMapper mapper;

    public ObjectTypeMyBatisStore(ObjectTypeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ObjectTypeRow> findByType(int objectType) {
        return Optional.ofNullable(mapper.selectByType(objectType));
    }

    @Override
    public Optional<ObjectTypeRow> findByCode(String code) {
        return Optional.ofNullable(mapper.selectByCode(code));
    }

    @Override
    public Page<ObjectTypeRow> search(String domain, String status, String q, Pageable pageable) {
        Pageable page = pageable != null ? pageable : Pageable.unpaged();
        String query = StringUtils.hasText(q) ? "%" + q.toLowerCase() + "%" : null;
        if (page.isUnpaged()) {
            List<ObjectTypeRow> rows = mapper.search(domain, status, query, null, null);
            return new PageImpl<>(rows, page, rows.size());
        }
        long total = mapper.count(domain, status, query);
        if (total == 0) {
            return Page.empty(page);
        }
        List<ObjectTypeRow> rows = mapper.search(domain, status, query, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, total);
    }

    @Override
    public ObjectTypeRow upsert(ObjectTypeRow row) {
        mapper.upsertType(row);
        return findByType(row.getObjectType()).orElse(row);
    }

    @Override
    public ObjectTypeRow patch(int objectType, ObjectTypeRow patch) {
        if (!hasPatchChanges(patch)) {
            return findByType(objectType).orElse(null);
        }
        Timestamp updatedAt = Timestamp.from(Optional.ofNullable(patch.getUpdatedAt()).orElseGet(Instant::now));
        mapper.patchType(objectType, patch, updatedAt);
        return findByType(objectType).orElse(null);
    }

    @Override
    public Optional<ObjectTypePolicyRow> findPolicy(int objectType) {
        return Optional.ofNullable(mapper.selectPolicyByType(objectType));
    }

    @Override
    public ObjectTypePolicyRow upsertPolicy(ObjectTypePolicyRow row) {
        mapper.upsertPolicy(row);
        return findPolicy(row.getObjectType()).orElse(row);
    }

    @Override
    public void delete(int objectType) {
        mapper.delete(objectType);
    }

    private boolean hasPatchChanges(ObjectTypeRow patch) {
        return StringUtils.hasText(patch.getCode())
                || StringUtils.hasText(patch.getName())
                || StringUtils.hasText(patch.getDomain())
                || StringUtils.hasText(patch.getStatus())
                || patch.getDescription() != null;
    }
}
