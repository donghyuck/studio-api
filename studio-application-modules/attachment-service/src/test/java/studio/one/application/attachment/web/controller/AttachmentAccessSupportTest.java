package studio.one.application.attachment.web.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import studio.one.application.attachment.application.result.AttachmentOwnerAccessAction;
import studio.one.application.attachment.application.result.AttachmentObjectTypeDescriptor;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.objecttype.application.WellKnownAttachmentObjectTypes;

class AttachmentAccessSupportTest {

    @Test
    void isAdminAcceptsLegacyAndSpringSecurityRoleNames() {
        assertTrue(AttachmentAccessSupport.isAdmin(principal("ADMIN")));
        assertTrue(AttachmentAccessSupport.isAdmin(principal("ROLE_ADMIN")));
    }

    @Test
    void metadataAttachmentDomainIsRecognizedBeforeLegacyFallback() {
        assertTrue(AttachmentAccessSupport.isWellKnownDomainAttachmentType(
                9001,
                objectType -> java.util.Optional.of(new AttachmentObjectTypeDescriptor(objectType, true, false, null))));
    }

    @Test
    void genericAttachmentIsExcludedFromDomainAuthorizerEvenWhenMetadataMatches() {
        assertFalse(AttachmentAccessSupport.isWellKnownDomainAttachmentType(
                WellKnownAttachmentObjectTypes.GENERIC_ATTACHMENT,
                objectType -> java.util.Optional.of(new AttachmentObjectTypeDescriptor(objectType, true, true, "generic"))));
    }

    @Test
    void legacyFallbackStillAppliesWhenMetadataDoesNotMarkAttachmentDomain() {
        assertTrue(AttachmentAccessSupport.isWellKnownDomainAttachmentType(
                WellKnownAttachmentObjectTypes.WORKSPACE_ATTACHMENT,
                objectType -> java.util.Optional.of(new AttachmentObjectTypeDescriptor(objectType, false, false, null))));
    }

    @Test
    void metadataAttachmentDomainRequiresOwnerAuthorizer() {
        Attachment attachment = mock(Attachment.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AttachmentOwnerAccessAuthorizer> authorizers = mock(ObjectProvider.class);

        when(attachment.getObjectType()).thenReturn(9001);
        when(attachment.getObjectId()).thenReturn(20L);
        when(authorizers.orderedStream()).thenReturn(Stream.empty());

        assertThrows(AccessDeniedException.class, () -> AttachmentAccessSupport.requireAttachmentAccess(
                attachment,
                principal("USER"),
                authorizers,
                objectType -> java.util.Optional.of(new AttachmentObjectTypeDescriptor(objectType, true, false, null)),
                AttachmentOwnerAccessAction.READ));
    }

    private ApplicationPrincipal principal(String role) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return 1L;
            }

            @Override
            public String getUsername() {
                return "admin";
            }

            @Override
            public Set<String> getRoles() {
                return Set.of(role);
            }
        };
    }
}
