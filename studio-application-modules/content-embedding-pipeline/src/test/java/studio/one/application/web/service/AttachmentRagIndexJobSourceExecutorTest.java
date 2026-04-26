package studio.one.application.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;

class AttachmentRagIndexJobSourceExecutorTest {

    @Test
    void supportsAttachmentSourceWithoutRawIndexRequest() {
        AttachmentRagIndexJobSourceExecutor executor = new AttachmentRagIndexJobSourceExecutor(
                mock(AttachmentRagIndexService.class));

        assertThat(executor.supports(new RagIndexJobCreateRequest(
                "custom",
                "42",
                "doc-1",
                "attachment",
                false,
                null), RagIndexJobSourceRequest.empty())).isTrue();
    }

    @Test
    void doesNotSupportObjectTypeOnlyAttachmentRequests() {
        AttachmentRagIndexJobSourceExecutor executor = new AttachmentRagIndexJobSourceExecutor(
                mock(AttachmentRagIndexService.class));

        assertThat(executor.supports(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "custom-source",
                false,
                null), RagIndexJobSourceRequest.empty())).isFalse();
    }

    @Test
    void delegatesAttachmentJobToAttachmentRagIndexService() throws Exception {
        AttachmentRagIndexService service = mock(AttachmentRagIndexService.class);
        AttachmentRagIndexCommand command = new AttachmentRagIndexCommand(
                "doc-1",
                "attachment",
                "42",
                Map.of("attachmentId", "42"),
                List.of("alpha"),
                true);
        when(service.command(
                eq(42L),
                eq("doc-1"),
                eq("attachment"),
                eq("42"),
                eq(Map.of("attachmentId", "42")),
                eq(List.of("alpha")),
                eq(true))).thenReturn(command);
        when(service.index(eq(42L), eq(command), any(RagIndexProgressListener.class)))
                .thenReturn(new AttachmentRagIndexResult(AttachmentRagIndexDiagnostics.fallback("structured_not_attempted")));
        AttachmentRagIndexJobSourceExecutor executor = new AttachmentRagIndexJobSourceExecutor(service);
        RagIndexJob job = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "attachment",
                Instant.parse("2026-04-26T00:00:00Z"));
        RagIndexJobCreateRequest request = new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null);
        RagIndexJobSourceRequest sourceRequest =
                new RagIndexJobSourceRequest(Map.of("attachmentId", "42"), List.of("alpha"), true);

        executor.execute(job, request, sourceRequest, RagIndexProgressListener.noop());

        verify(service).index(eq(42L), eq(command), any(RagIndexProgressListener.class));
    }
}
