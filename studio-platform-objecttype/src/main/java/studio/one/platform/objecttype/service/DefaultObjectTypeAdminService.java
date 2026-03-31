package studio.one.platform.objecttype.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import java.util.List;

import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.db.ObjectTypeStore;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypePolicyRow;
import studio.one.platform.objecttype.db.jdbc.model.ObjectTypeRow;
import studio.one.platform.objecttype.error.ObjectTypeErrorCodes;

public class DefaultObjectTypeAdminService implements ObjectTypeAdminService {

    private final ObjectTypeStore store;

    public DefaultObjectTypeAdminService(ObjectTypeStore store) {
        this.store = store;
    }

    @Override
    public List<ObjectTypeView> search(String domain, String status, String q) {
        String normalizedStatus = ObjectTypeValidation.normalizeStatus(status);
        return store.search(domain, normalizedStatus, q, Pageable.unpaged())
                .map(DefaultObjectTypeAdminService::toView)
                .getContent();
    }

    @Override
    public ObjectTypeView get(int objectType) {
        ObjectTypeRow row = store.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
        return toView(row);
    }

    @Override
    public ObjectTypeView upsert(ObjectTypeUpsertCommand request) {
        if (request.objectType() == null) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.VALIDATION_ERROR, "objectType");
        }
        ObjectTypeValidation.validateStatus(request.status());
        String normalizedStatus = ObjectTypeValidation.normalizeStatus(request.status());
        checkCodeConflict(request.objectType(), request.code());
        ObjectTypeRow row = new ObjectTypeRow();
        row.setObjectType(request.objectType());
        row.setCode(request.code());
        row.setName(request.name());
        row.setDomain(request.domain());
        row.setStatus(normalizedStatus);
        row.setDescription(request.description());
        row.setCreatedBy(request.createdBy());
        row.setCreatedById(request.createdById());
        row.setCreatedAt(Instant.now());
        row.setUpdatedBy(request.updatedBy());
        row.setUpdatedById(request.updatedById());
        row.setUpdatedAt(Instant.now());
        ObjectTypeRow saved = store.upsert(row);
        return toView(saved);
    }

    @Override
    public ObjectTypeView patch(int objectType, ObjectTypePatchCommand request) {
        ObjectTypeRow existing = store.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
        if (request.status() != null) {
            ObjectTypeValidation.validateStatus(request.status());
        }
        if (request.code() != null && !request.code().equalsIgnoreCase(existing.getCode())) {
            checkCodeConflict(objectType, request.code());
        }
        ObjectTypeRow patch = new ObjectTypeRow();
        patch.setCode(request.code());
        patch.setName(request.name());
        patch.setDomain(request.domain());
        patch.setStatus(ObjectTypeValidation.normalizeStatus(request.status()));
        patch.setDescription(request.description());
        patch.setUpdatedBy(request.updatedBy());
        patch.setUpdatedById(request.updatedById());
        patch.setUpdatedAt(Instant.now());
        ObjectTypeRow saved = store.patch(objectType, patch);
        return toView(saved != null ? saved : existing);
    }

    @Override
    public ObjectTypePolicyView getPolicy(int objectType) {
        ensureTypeExists(objectType);
        return store.findPolicy(objectType)
                .map(DefaultObjectTypeAdminService::toPolicyView)
                .orElse(null);
    }

    @Override
    public ObjectTypePolicyView upsertPolicy(int objectType, ObjectTypePolicyUpsertCommand request) {
        ensureTypeExists(objectType);
        if (request.maxFileMb() != null && request.maxFileMb() < 0) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.VALIDATION_ERROR, "maxFileMb");
        }
        ObjectTypePolicyRow row = new ObjectTypePolicyRow();
        row.setObjectType(objectType);
        row.setMaxFileMb(request.maxFileMb());
        row.setAllowedExt(ObjectTypeValidation.normalizeExt(request.allowedExt()));
        row.setAllowedMime(ObjectTypeValidation.normalizeMime(request.allowedMime()));
        row.setPolicyJson(request.policyJson());
        row.setCreatedBy(request.createdBy());
        row.setCreatedById(request.createdById());
        row.setCreatedAt(Instant.now());
        row.setUpdatedBy(request.updatedBy());
        row.setUpdatedById(request.updatedById());
        row.setUpdatedAt(Instant.now());
        ObjectTypePolicyRow saved = store.upsertPolicy(row);
        return toPolicyView(saved);
    }

    private void ensureTypeExists(int objectType) {
        store.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
    }

    private void checkCodeConflict(int objectType, String code) {
        Optional<ObjectTypeRow> existing = store.findByCode(code);
        if (existing.isPresent() && existing.get().getObjectType() != objectType) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.CONFLICT, code);
        }
    }

    static ObjectTypeView toView(ObjectTypeRow row) {
        return new ObjectTypeView(
                row.getObjectType(),
                row.getCode(),
                row.getName(),
                row.getDomain(),
                row.getStatus(),
                row.getDescription(),
                row.getCreatedBy(),
                row.getCreatedById(),
                toOffset(row.getCreatedAt()),
                row.getUpdatedBy(),
                row.getUpdatedById(),
                toOffset(row.getUpdatedAt()));
    }

    static ObjectTypePolicyView toPolicyView(ObjectTypePolicyRow row) {
        return new ObjectTypePolicyView(
                row.getObjectType(),
                row.getMaxFileMb(),
                row.getAllowedExt(),
                row.getAllowedMime(),
                row.getPolicyJson(),
                row.getCreatedBy(),
                row.getCreatedById(),
                toOffset(row.getCreatedAt()),
                row.getUpdatedBy(),
                row.getUpdatedById(),
                toOffset(row.getUpdatedAt()));
    }

    private static OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
