package studio.one.application.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;

class AttachmentRagIndexJobSourceNameResolverTest {

    @Test
    void supportsAttachmentSource() {
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(
                mock(AttachmentService.class));

        assertThat(resolver.supports(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "42",
                "attachment",
                false,
                null), RagIndexJobSourceRequest.empty())).isTrue();
    }

    @Test
    void doesNotSupportRawTextIndexRequests() {
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(
                mock(AttachmentService.class));

        assertThat(resolver.supports(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "42",
                "attachment",
                false,
                new studio.one.platform.ai.core.rag.RagIndexRequest(
                        "42",
                        "raw text",
                        Map.of(),
                        java.util.List.of(),
                        false)), RagIndexJobSourceRequest.empty())).isFalse();
    }

    @Test
    void resolvesAttachmentNameFromMetadataAttachmentId() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getName()).thenReturn("sample.pdf");
        when(attachmentService.getAttachmentById(99L)).thenReturn(attachment);
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(attachmentService);

        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "42",
                        "42",
                        "attachment",
                        false,
                        null),
                new RagIndexJobSourceRequest(Map.of("attachmentId", "99"), java.util.List.of(), false)))
                .contains("sample.pdf");
        verify(attachmentService).getAttachmentById(99L);
    }

    @Test
    void resolvesAttachmentNameFromObjectIdWhenMetadataAttachmentIdIsMissing() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getName()).thenReturn(" object.pdf ");
        when(attachmentService.getAttachmentById(42L)).thenReturn(attachment);
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(attachmentService);

        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "42",
                        "42",
                        "attachment",
                        false,
                        null),
                RagIndexJobSourceRequest.empty()))
                .contains("object.pdf");
    }

    @Test
    void returnsEmptyWhenAttachmentIdIsInvalidOrLookupFails() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        when(attachmentService.getAttachmentById(42L)).thenThrow(new RuntimeException("missing attachment"));
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(attachmentService);

        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "not-numeric",
                        "not-numeric",
                        "attachment",
                        false,
                        null),
                RagIndexJobSourceRequest.empty())).isEmpty();
        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "42",
                        "42",
                        "attachment",
                        false,
                        null),
                RagIndexJobSourceRequest.empty())).isEmpty();
    }
}
