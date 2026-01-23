package studio.one.platform.objecttype.service;

import java.util.Locale;
import java.util.Map;

import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.error.ObjectTypeErrorCodes;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.model.ObjectTypeStatus;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;
import studio.one.platform.objecttype.web.dto.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.ObjectTypeDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyDto;
import studio.one.platform.objecttype.web.dto.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.ValidateUploadResponse;

public class DefaultObjectTypeRuntimeService implements ObjectTypeRuntimeService {

    private final ObjectTypeRegistry registry;
    private final ObjectPolicyResolver policyResolver;

    public DefaultObjectTypeRuntimeService(ObjectTypeRegistry registry, ObjectPolicyResolver policyResolver) {
        this.registry = registry;
        this.policyResolver = policyResolver;
    }

    @Override
    public ObjectTypeDefinitionDto definition(int objectType) {
        ObjectTypeMetadata meta = registry.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
        requireActive(meta);
        ObjectPolicy policy = policyResolver.resolve(meta).orElse(null);
        return ObjectTypeDefinitionDto.builder()
                .type(toDto(meta))
                .policy(toPolicyDto(policy, objectType))
                .build();
    }

    @Override
    public ValidateUploadResponse validateUpload(int objectType, ValidateUploadRequest request) {
        ObjectTypeMetadata meta = registry.findByType(objectType)
                .orElseThrow(() -> PlatformRuntimeException.of(ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE, objectType));
        requireActive(meta);
        ObjectPolicy policy = policyResolver.resolve(meta).orElse(null);
        if (policy == null) {
            return ValidateUploadResponse.builder().allowed(true).build();
        }
        Map<String, Object> attrs = policy.getAttributes();
        Integer maxFileMb = asInt(attrs.get("maxFileMb"));
        if (maxFileMb != null && request.sizeBytes() != null) {
            long maxBytes = maxFileMb.longValue() * 1024L * 1024L;
            if (request.sizeBytes() > maxBytes) {
                throw PlatformRuntimeException.of(ObjectTypeErrorCodes.POLICY_VIOLATION, "maxFileMb");
            }
        }
        String ext = extension(request.fileName());
        String allowedExt = normalize(attrs.get("allowedExt"));
        if (ext != null && allowedExt != null && !allowedExt.isEmpty()) {
            if (!containsToken(allowedExt, ext.toLowerCase(Locale.ROOT))) {
                throw PlatformRuntimeException.of(ObjectTypeErrorCodes.POLICY_VIOLATION, "allowedExt");
            }
        }
        String contentType = request.contentType() != null ? request.contentType().toLowerCase(Locale.ROOT) : null;
        String allowedMime = normalize(attrs.get("allowedMime"));
        if (contentType != null && allowedMime != null && !allowedMime.isEmpty()) {
            if (!mimeAllowed(allowedMime, contentType)) {
                throw PlatformRuntimeException.of(ObjectTypeErrorCodes.POLICY_VIOLATION, "allowedMime");
            }
        }
        return ValidateUploadResponse.builder().allowed(true).build();
    }

    private void requireActive(ObjectTypeMetadata meta) {
        Object raw = meta.getAttributes().get("status");
        if (raw == null) {
            return;
        }
        ObjectTypeStatus status = ObjectTypeStatus.from(String.valueOf(raw));
        if (status == null) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.VALIDATION_ERROR, "status");
        }
        if (status == ObjectTypeStatus.DISABLED) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.OBJECT_TYPE_DISABLED, meta.getObjectType());
        }
        if (status == ObjectTypeStatus.DEPRECATED) {
            throw PlatformRuntimeException.of(ObjectTypeErrorCodes.OBJECT_TYPE_DEPRECATED, meta.getObjectType());
        }
    }

    private ObjectTypeDto toDto(ObjectTypeMetadata meta) {
        return ObjectTypeDto.builder()
                .objectType(meta.getObjectType())
                .code(meta.getKey())
                .name(meta.getName())
                .domain(asString(meta.getAttributes().get("domain")))
                .status(asString(meta.getAttributes().get("status")))
                .description(asString(meta.getAttributes().get("description")))
                .build();
    }

    private ObjectTypePolicyDto toPolicyDto(ObjectPolicy policy, int objectType) {
        if (policy == null) {
            return null;
        }
        Map<String, Object> attrs = policy.getAttributes();
        return ObjectTypePolicyDto.builder()
                .objectType(objectType)
                .maxFileMb(asInt(attrs.get("maxFileMb")))
                .allowedExt(asString(attrs.get("allowedExt")))
                .allowedMime(asString(attrs.get("allowedMime")))
                .policyJson(asString(attrs.get("policyJson")))
                .build();
    }

    private String extension(String name) {
        if (name == null) {
            return null;
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private boolean containsToken(String list, String token) {
        for (String part : list.split(",")) {
            if (token.equals(part.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean mimeAllowed(String allowed, String contentType) {
        for (String part : allowed.split(",")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            if (p.endsWith("/*")) {
                String prefix = p.substring(0, p.length() - 2);
                if (contentType.startsWith(prefix + "/")) {
                    return true;
                }
            } else if (contentType.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(Object raw) {
        if (raw == null) {
            return null;
        }
        return String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Integer asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
