package studio.one.platform.objecttype;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.objecttype.error.ObjectTypeErrorCodes;
import studio.one.platform.objecttype.model.ObjectPolicy;
import studio.one.platform.objecttype.model.ObjectTypeMetadata;
import studio.one.platform.objecttype.policy.ObjectPolicyResolver;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;
import studio.one.platform.objecttype.service.DefaultObjectTypeRuntimeService;
import studio.one.platform.objecttype.web.dto.ValidateUploadRequest;
import studio.one.platform.objecttype.yaml.YamlObjectPolicy;
import studio.one.platform.objecttype.yaml.YamlObjectTypeMetadata;

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
                () -> svc.validateUpload(1, new ValidateUploadRequest("a.pdf", "application/pdf", 10_000L)));
    }
}
