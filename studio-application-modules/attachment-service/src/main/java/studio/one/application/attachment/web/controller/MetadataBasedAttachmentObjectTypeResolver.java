package studio.one.application.attachment.web.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.result.AttachmentObjectTypeDescriptor;
import studio.one.application.attachment.application.usecase.AttachmentObjectTypeResolver;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

public class MetadataBasedAttachmentObjectTypeResolver implements AttachmentObjectTypeResolver {

    private final ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider;
    private final ObjectProvider<ObjectTypeRegistry> objectTypeRegistryProvider;

    public MetadataBasedAttachmentObjectTypeResolver(
            ObjectProvider<ObjectTypeRuntimeService> objectTypeRuntimeServiceProvider,
            ObjectProvider<ObjectTypeRegistry> objectTypeRegistryProvider) {
        this.objectTypeRuntimeServiceProvider = objectTypeRuntimeServiceProvider;
        this.objectTypeRegistryProvider = objectTypeRegistryProvider;
    }

    @Override
    public Optional<AttachmentObjectTypeDescriptor> resolve(int objectType) {
        return resolveFromMetadata(objectType)
                .or(() -> resolveFromRuntimeDefinition(objectType));
    }

    private Optional<AttachmentObjectTypeDescriptor> resolveFromMetadata(int objectType) {
        ObjectTypeRegistry registry = objectTypeRegistryProvider.getIfAvailable();
        if (registry == null) {
            return Optional.empty();
        }
        return registry.findByType(objectType).map(this::resolveFromMetadata);
    }

    private Optional<AttachmentObjectTypeDescriptor> resolveFromRuntimeDefinition(int objectType) {
        ObjectTypeRuntimeService runtimeService = objectTypeRuntimeServiceProvider.getIfAvailable();
        if (runtimeService == null) {
            return Optional.empty();
        }
        ObjectTypeDefinition definition = runtimeService.definition(objectType);
        return Optional.of(resolveFromRuntimeDefinition(definition));
    }

    private AttachmentObjectTypeDescriptor resolveFromMetadata(ObjectTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAttributes();
        if (attrs == null) {
            String keyAttachmentType = attachmentTypeFromKey(metadata.getKey());
            return new AttachmentObjectTypeDescriptor(
                    metadata.getObjectType(),
                    keyAttachmentType != null,
                    false,
                    keyAttachmentType);
        }
        String domain = asString(attrs.get("domain"));
        boolean attachmentEnabled = attachmentEnabled(attrs);
        String attachmentType = attachmentType(attrs);
        if (attachmentType == null) {
            attachmentType = attachmentTypeFromKey(metadata.getKey());
        }
        boolean domainFlag = "attachment".equalsIgnoreCase(domain) || attachmentEnabled || attachmentType != null;
        return new AttachmentObjectTypeDescriptor(metadata.getObjectType(), domainFlag, attachmentEnabled, attachmentType);
    }

    private AttachmentObjectTypeDescriptor resolveFromRuntimeDefinition(ObjectTypeDefinition definition) {
        String domain = definition != null && definition.type() != null ? definition.type().domain() : null;
        String attachmentType = definition != null && definition.type() != null
                ? attachmentTypeFromKey(definition.type().code())
                : null;
        return new AttachmentObjectTypeDescriptor(
                definition != null && definition.type() != null ? definition.type().objectType() : -1,
                "attachment".equalsIgnoreCase(domain) || attachmentType != null,
                false,
                attachmentType);
    }

    private boolean attachmentEnabled(Map<String, Object> attrs) {
        Object raw = attrs.get("attachment");
        if (raw instanceof Map<?, ?> attachmentMap) {
            Object enabled = attachmentMap.get("enabled");
            Boolean parsed = asBoolean(enabled);
            if (parsed != null) {
                return parsed;
            }
            return false;
        }
        Boolean parsed = asBoolean(attrs.get("attachment.enabled"));
        return parsed != null && parsed;
    }

    private String attachmentType(Map<String, Object> attrs) {
        Object raw = attrs.get("attachment");
        if (raw instanceof Map<?, ?> attachmentMap) {
            Object typeValue = attachmentMap.get("type");
            return asString(typeValue);
        }
        return asString(attrs.get("attachment.type"));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String attachmentTypeFromKey(String key) {
        String normalized = asString(key);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase();
        if (lower.equals("attachment")
                || lower.endsWith("-attachment")
                || lower.endsWith("_attachment")
                || lower.endsWith(".attachment")
                || lower.endsWith(":attachment")
                || lower.endsWith("/attachment")) {
            return normalized;
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) {
            return false;
        }
        return null;
    }
}
