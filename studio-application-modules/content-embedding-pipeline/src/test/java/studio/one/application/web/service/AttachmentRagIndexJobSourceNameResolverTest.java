package studio.one.application.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.application.usecase.AttachmentService;
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
    void resolvesAttachmentNameFromMatchingMetadataAttachmentId() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getName()).thenReturn("sample.pdf");
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
                new RagIndexJobSourceRequest(Map.of("attachmentId", "042"), java.util.List.of(), false)))
                .contains("sample.pdf");
        verify(attachmentService).getAttachmentById(42L);
    }

    @Test
    void rejectsMismatchedAttachmentSelectorBeforeLookup() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(attachmentService);

        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "project",
                        "6",
                        "doc-1",
                        " attachment ",
                        false,
                        null),
                new RagIndexJobSourceRequest(Map.of("attachmentId", "6"), java.util.List.of(), false)))
                .isEmpty();
        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "6",
                        "doc-1",
                        "attachment",
                        false,
                        null),
                new RagIndexJobSourceRequest(Map.of("attachmentId", "7"), java.util.List.of(), false)))
                .isEmpty();
        verify(attachmentService, never()).getAttachmentById(6L);
        verify(attachmentService, never()).getAttachmentById(7L);
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
    void rejectsBlankMetadataAttachmentIdBeforeLookup() throws Exception {
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentRagIndexJobSourceNameResolver resolver = new AttachmentRagIndexJobSourceNameResolver(attachmentService);

        assertThat(resolver.resolveSourceName(
                new RagIndexJobCreateRequest(
                        "attachment",
                        "42",
                        "42",
                        "attachment",
                        false,
                        null),
                new RagIndexJobSourceRequest(Map.of("attachmentId", "   "), java.util.List.of(), false)))
                .isEmpty();
        verify(attachmentService, never()).getAttachmentById(42L);
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
