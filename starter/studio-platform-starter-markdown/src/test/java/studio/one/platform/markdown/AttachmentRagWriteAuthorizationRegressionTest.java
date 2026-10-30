package studio.one.platform.markdown;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.usecase.AttachmentObjectTypeResolver;
import studio.one.application.attachment.application.usecase.AttachmentOwnerAccessAuthorizer;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.web.controller.AttachmentRagObjectAuthorizer;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.objecttype.application.WellKnownAttachmentObjectTypes;

class AttachmentRagWriteAuthorizationRegressionTest {

    @Test
    void starterMarkdownUsesAttachmentRagAuthorizerWriteBoundary() {
        AttachmentService attachments = mock(AttachmentService.class);
        when(attachments.getAttachmentById(17L)).thenReturn(attachment(
                WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT,
                900L,
                5L));
        AttachmentOwnerAccessAuthorizer owner = mock(AttachmentOwnerAccessAuthorizer.class);
        when(owner.supports(WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT)).thenReturn(true);
        when(owner.canIndex(
                org.mockito.ArgumentMatchers.eq(WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT),
                org.mockito.ArgumentMatchers.eq(900L),
                org.mockito.ArgumentMatchers.any(ApplicationPrincipal.class))).thenReturn(true);

        AttachmentRagObjectAuthorizer authorizer = authorizer(
                attachments,
                principal(6L),
                Stream.of(owner));

        assertThat(authorizer.canWrite("attachment", "17")).isTrue();
    }

    @Test
    void starterMarkdownRejectsLegacyOwnerAccessWithoutExplicitIndexPermission() {
        AttachmentService attachments = mock(AttachmentService.class);
        when(attachments.getAttachmentById(17L)).thenReturn(attachment(
                WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT,
                900L,
                5L));
        AttachmentOwnerAccessAuthorizer owner = mock(AttachmentOwnerAccessAuthorizer.class);
        when(owner.supports(WellKnownAttachmentObjectTypes.WIKI_ATTACHMENT)).thenReturn(true);

        AttachmentRagObjectAuthorizer authorizer = authorizer(
                attachments,
                principal(6L),
                Stream.of(owner));

        assertThat(authorizer.canWrite("attachment", "17")).isFalse();
    }

    private AttachmentRagObjectAuthorizer authorizer(
            AttachmentService attachments,
            ApplicationPrincipal principal,
            Stream<AttachmentOwnerAccessAuthorizer> owners) {
        ObjectProvider<PrincipalResolver> principals = mock(ObjectProvider.class);
        PrincipalResolver resolver = mock(PrincipalResolver.class);
        when(principals.getIfAvailable()).thenReturn(resolver);
        when(resolver.currentOrNull()).thenReturn(principal);
        ObjectProvider<AttachmentOwnerAccessAuthorizer> ownerProvider = mock(ObjectProvider.class);
        when(ownerProvider.orderedStream()).thenReturn(owners);
        AttachmentObjectTypeResolver objectTypes = mock(AttachmentObjectTypeResolver.class);
        when(objectTypes.resolve(org.mockito.ArgumentMatchers.anyInt())).thenReturn(Optional.empty());
        return new AttachmentRagObjectAuthorizer(attachments, principals, ownerProvider, objectTypes);
    }

    private ApplicationPrincipal principal(Long userId) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return "user-" + userId;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("USER");
            }
        };
    }

    private Attachment attachment(int objectType, long objectId, long createdBy) {
        return new Attachment() {
            @Override
            public int getObjectType() {
                return objectType;
            }

            @Override
            public long getObjectId() {
                return objectId;
            }

            @Override
            public long getAttachmentId() {
                return 17L;
            }

            @Override
            public void setAttachmentId(long attachementId) {
            }

            @Override
            public String getName() {
                return "sample.md";
            }

            @Override
            public void setName(String name) {
            }

            @Override
            public long getSize() {
                return 100L;
            }

            @Override
            public void setSize(long size) {
            }

            @Override
            public String getContentType() {
                return "text/markdown";
            }

            @Override
            public void setContentType(String contentType) {
            }

            @Override
            public Map<String, String> getProperties() {
                return Map.of();
            }

            @Override
            public void setProperties(Map<String, String> properties) {
            }

            @Override
            public long getCreatedBy() {
                return createdBy;
            }

            @Override
            public void setCreatedBy(long userId) {
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.EPOCH;
            }

            @Override
            public Instant getUpdatedAt() {
                return Instant.EPOCH;
            }
        };
    }
}
