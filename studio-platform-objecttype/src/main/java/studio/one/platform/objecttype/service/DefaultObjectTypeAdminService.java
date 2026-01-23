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
import studio.one.platform.objecttype.web.dto.ObjectTypeDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePatchRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypeUpsertRequest;

public class DefaultObjectTypeAdminService implements ObjectTypeAdminService {

    private final ObjectTypeStore store;

    public DefaultObjectTypeAdminService(ObjectTypeStore store) {
        this.store = store;
    }

    @Override
    public List<ObjectTypeDto> search(String domain, String status, String q) {
        String normalizedStatus = ObjectTypeValidation.normalizeStatus(status);
        return store.search(domain, normalizedStatus, q, Pageable.unpaged())
                .map(DefaultObjectTypeAdminService::toDto)
                .getContent();
    }

    @Override
    public ObjectTypeDto get(int objectType) {
        ObjectTypeRow row = store.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
        return toDto(row);
    }

    @Override
    public ObjectTypeDto upsert(ObjectTypeUpsertRequest request) {
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
        return toDto(saved);
    }

    @Override
    public ObjectTypeDto patch(int objectType, ObjectTypePatchRequest request) {
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
        return toDto(saved != null ? saved : existing);
    }

    @Override
    public ObjectTypePolicyDto getPolicy(int objectType) {
        ensureTypeExists(objectType);
        return store.findPolicy(objectType)
                .map(DefaultObjectTypeAdminService::toPolicyDto)
                .orElse(null);
    }

    @Override
    public ObjectTypePolicyDto upsertPolicy(int objectType, ObjectTypePolicyUpsertRequest request) {
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
        return toPolicyDto(saved);
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

    static ObjectTypeDto toDto(ObjectTypeRow row) {
        return ObjectTypeDto.builder()
                .objectType(row.getObjectType())
                .code(row.getCode())
                .name(row.getName())
                .domain(row.getDomain())
                .status(row.getStatus())
                .description(row.getDescription())
                .createdBy(row.getCreatedBy())
                .createdById(row.getCreatedById())
                .createdAt(toOffset(row.getCreatedAt()))
                .updatedBy(row.getUpdatedBy())
                .updatedById(row.getUpdatedById())
                .updatedAt(toOffset(row.getUpdatedAt()))
                .build();
    }

    static ObjectTypePolicyDto toPolicyDto(ObjectTypePolicyRow row) {
        return ObjectTypePolicyDto.builder()
                .objectType(row.getObjectType())
                .maxFileMb(row.getMaxFileMb())
                .allowedExt(row.getAllowedExt())
                .allowedMime(row.getAllowedMime())
                .policyJson(row.getPolicyJson())
                .createdBy(row.getCreatedBy())
                .createdById(row.getCreatedById())
                .createdAt(toOffset(row.getCreatedAt()))
                .updatedBy(row.getUpdatedBy())
                .updatedById(row.getUpdatedById())
                .updatedAt(toOffset(row.getUpdatedAt()))
                .build();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
