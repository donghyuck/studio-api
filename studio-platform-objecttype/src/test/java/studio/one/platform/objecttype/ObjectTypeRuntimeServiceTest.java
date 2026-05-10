package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.domain.error.ObjectTypeErrorCodes;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;
import studio.one.platform.objecttype.application.service.DefaultObjectTypeRuntimeService;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.infrastructure.yaml.YamlObjectPolicy;
import studio.one.platform.objecttype.infrastructure.yaml.YamlObjectTypeMetadata;

public class ObjectTypeRuntimeServiceTest {

    @Test
    void requireActiveRejectsDisabled() {
        ObjectTypeMetadata meta = new YamlObjectTypeMetadata(1, "attachment", "Attachment",
                Map.of("status", "disabled"));
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.of(meta);
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return Optional.of(meta);
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.empty();
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        PlatformRuntimeException ex = assertThrows(PlatformRuntimeException.class, () -> svc.definition(1));
        assertTrue(ex.getType() == ObjectTypeErrorCodes.OBJECT_TYPE_DISABLED);
    }

    @Test
    void validateUploadEnforcesPolicy() {
        ObjectTypeMetadata meta = new YamlObjectTypeMetadata(1, "attachment", "Attachment",
                Map.of("status", "active"));
        ObjectPolicy policy = new YamlObjectPolicy("p1", Map.of(
                "maxFileMb", 1,
                "allowedExt", "png",
                "allowedMime", "image/png"));
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.of(meta);
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return Optional.of(meta);
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.of(policy);
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.of(policy);
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        assertThrows(PlatformRuntimeException.class,
                () -> svc.validateUpload(1, new ValidateUploadCommand("a.pdf", "application/pdf", 10_000L)));
    }

    @Test
    void validateUploadReturnsAllowedWhenPolicyMatches() {
        ObjectTypeMetadata meta = new YamlObjectTypeMetadata(1, "attachment", "Attachment",
                Map.of("status", "active"));
        ObjectPolicy policy = new YamlObjectPolicy("p1", Map.of(
                "maxFileMb", 1,
                "allowedExt", "png,jpg",
                "allowedMime", "image/*"));
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.of(meta);
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return Optional.of(meta);
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.of(policy);
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.of(policy);
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        assertEquals(true, svc.validateUpload(1, new ValidateUploadCommand("a.png", "image/png", 10_000L)).allowed());
    }

    @Test
    void resolvesDefinitionAndTypeByKey() {
        ObjectTypeMetadata meta = new YamlObjectTypeMetadata(2103, "workspace-attachment", "Workspace Attachment",
                Map.of("status", "active", "domain", "workspace"));
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return "workspace-attachment".equals(key) ? Optional.of(meta) : Optional.empty();
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.empty();
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        assertEquals(2103, svc.objectTypeByKey("workspace-attachment"));
        assertEquals(2103, svc.definitionByKey("workspace-attachment").type().objectType());
    }

    @Test
    void unknownKeyDoesNotFallbackToGenericAttachment() {
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return Optional.empty();
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.empty();
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        PlatformRuntimeException ex = assertThrows(PlatformRuntimeException.class,
                () -> svc.objectTypeByKey("missing-attachment"));
        assertTrue(ex.getType() == ObjectTypeErrorCodes.UNKNOWN_OBJECT_TYPE);
    }

    @Test
    void validateUploadByKeyEnforcesPolicy() {
        ObjectTypeMetadata meta = new YamlObjectTypeMetadata(2104, "wiki-attachment", "Wiki Attachment",
                Map.of("status", "active"));
        ObjectPolicy policy = new YamlObjectPolicy("wiki-policy", Map.of(
                "maxFileMb", 1,
                "allowedExt", "pdf",
                "allowedMime", "application/pdf"));
        ObjectTypeRegistry registry = new ObjectTypeRegistry() {
            @Override
            public Optional<ObjectTypeMetadata> findByType(int objectType) {
                return Optional.empty();
            }

            @Override
            public Optional<ObjectTypeMetadata> findByKey(String key) {
                return "wiki-attachment".equals(key) ? Optional.of(meta) : Optional.empty();
            }
        };
        ObjectPolicyResolver resolver = new ObjectPolicyResolver() {
            @Override
            public Optional<ObjectPolicy> resolve(ObjectTypeMetadata metadata) {
                return Optional.of(policy);
            }

            @Override
            public Optional<ObjectPolicy> resolve(studio.one.platform.domain.model.TypeObject object) {
                return Optional.of(policy);
            }
        };
        DefaultObjectTypeRuntimeService svc = new DefaultObjectTypeRuntimeService(registry, resolver);

        assertThrows(PlatformRuntimeException.class,
                () -> svc.validateUploadByKey("wiki-attachment",
                        new ValidateUploadCommand("a.exe", "application/octet-stream", 10L)));
        assertEquals(true, svc.validateUploadByKey("wiki-attachment",
                new ValidateUploadCommand("a.pdf", "application/pdf", 10L)).allowed());
    }
}
