package studio.one.application.attachment.web.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.result.AttachmentObjectTypeDescriptor;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.objecttype.infrastructure.yaml.YamlObjectTypeMetadata;
import studio.one.platform.objecttype.registry.ObjectTypeRegistry;

class MetadataBasedAttachmentObjectTypeResolverTest {

    @Test
    void keySuffixMarksAdminCreatedAttachmentTypeWhenAttributesDoNotContainAttachmentFlag() {
        ObjectTypeRegistry registry = mock(ObjectTypeRegistry.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectTypeRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectTypeRuntimeService> runtimeServiceProvider = mock(ObjectProvider.class);
        MetadataBasedAttachmentObjectTypeResolver resolver = new MetadataBasedAttachmentObjectTypeResolver(
                runtimeServiceProvider,
                registryProvider);

        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registry.findByType(9001)).thenReturn(Optional.of(new YamlObjectTypeMetadata(
                9001,
                "forum-attachment",
                "Forum Attachment",
                Map.of("domain", "forum"))));

        Optional<AttachmentObjectTypeDescriptor> descriptor = resolver.resolve(9001);

        assertTrue(descriptor.isPresent());
        assertTrue(descriptor.get().isAttachmentDomainType());
    }

    @Test
    void runtimeDefinitionFailureIsPropagatedForAccessSupportFailClosedHandling() {
        ObjectTypeRuntimeService runtimeService = mock(ObjectTypeRuntimeService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectTypeRegistry> registryProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectTypeRuntimeService> runtimeServiceProvider = mock(ObjectProvider.class);
        MetadataBasedAttachmentObjectTypeResolver resolver = new MetadataBasedAttachmentObjectTypeResolver(
                runtimeServiceProvider,
                registryProvider);

        when(registryProvider.getIfAvailable()).thenReturn(null);
        when(runtimeServiceProvider.getIfAvailable()).thenReturn(runtimeService);
        when(runtimeService.definition(9001)).thenThrow(new IllegalStateException("metadata unavailable"));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(9001));
    }
}
