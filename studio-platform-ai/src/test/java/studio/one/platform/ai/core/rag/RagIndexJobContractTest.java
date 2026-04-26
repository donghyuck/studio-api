package studio.one.platform.ai.core.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.service.pipeline.RagIndexProgressListener;
import studio.one.platform.ai.service.pipeline.RagPipelineService;

class RagIndexJobContractTest {

    @Test
    void pendingJobNormalizesScopeAndInitializesCounts() {
        RagIndexJob job = RagIndexJob.pending(
                " job-1 ",
                " attachment ",
                " 42 ",
                " doc-1 ",
                " attachment ",
                Instant.parse("2026-04-26T00:00:00Z"));

        assertThat(job.jobId()).isEqualTo("job-1");
        assertThat(job.objectType()).isEqualTo("attachment");
        assertThat(job.objectId()).isEqualTo("42");
        assertThat(job.status()).isEqualTo(RagIndexJobStatus.PENDING);
        assertThat(job.chunkCount()).isZero();
        assertThat(job.durationMs()).isNull();
    }

    @Test
    void statusTransitionSeparatesStatusAndStep() {
        Instant startedAt = Instant.parse("2026-04-26T00:00:00Z");
        Instant finishedAt = Instant.parse("2026-04-26T00:00:01Z");
        RagIndexJob running = RagIndexJob.pending("job-1", "attachment", "42", "doc-1", "attachment", startedAt)
                .withStatus(RagIndexJobStatus.RUNNING, RagIndexJobStep.CHUNKING, null, startedAt);

        RagIndexJob completed = running.withStatus(
                RagIndexJobStatus.WARNING,
                RagIndexJobStep.COMPLETED,
                null,
                finishedAt);

        assertThat(completed.status()).isEqualTo(RagIndexJobStatus.WARNING);
        assertThat(completed.currentStep()).isEqualTo(RagIndexJobStep.COMPLETED);
        assertThat(completed.durationMs()).isEqualTo(1000L);
    }

    @Test
    void createRequestCanWrapLegacyIndexRequest() {
        RagIndexRequest indexRequest = new RagIndexRequest(
                "doc-1",
                "content",
                Map.of("objectType", "attachment", "objectId", "42", "sourceType", "attachment"));

        RagIndexJobCreateRequest request = RagIndexJobCreateRequest.forIndexRequest(indexRequest);

        assertThat(request.objectType()).isEqualTo("attachment");
        assertThat(request.objectId()).isEqualTo("42");
        assertThat(request.documentId()).isEqualTo("doc-1");
        assertThat(request.indexRequest()).isSameAs(indexRequest);
    }

    @Test
    void pageRequestKeepsLegacyConstructorAndNormalizesSorting() {
        RagIndexJobPageRequest legacy = new RagIndexJobPageRequest(-1, 0);

        assertThat(legacy.offset()).isZero();
        assertThat(legacy.limit()).isEqualTo(50);
        assertThat(legacy.sort()).isEqualTo(RagIndexJobPageRequest.Sort.CREATED_AT);
        assertThat(legacy.direction()).isEqualTo(RagIndexJobPageRequest.Direction.DESC);
        assertThat(RagIndexJobPageRequest.Sort.from("document-id"))
                .isEqualTo(RagIndexJobPageRequest.Sort.DOCUMENT_ID);
        assertThat(RagIndexJobPageRequest.Direction.from("asc"))
                .isEqualTo(RagIndexJobPageRequest.Direction.ASC);
        assertThat(RagIndexJobPageRequest.Sort.from("unknown"))
                .isEqualTo(RagIndexJobPageRequest.Sort.CREATED_AT);
    }

    @Test
    void pipelineListenerOverloadDelegatesToLegacyIndexByDefault() {
        CapturingRagPipelineService service = new CapturingRagPipelineService();
        RagIndexRequest request = new RagIndexRequest("doc-1", "content", Map.of());

        service.index(request, RagIndexProgressListener.noop());

        assertThat(service.indexedRequest).isSameAs(request);
    }

    private static class CapturingRagPipelineService implements RagPipelineService {

        private RagIndexRequest indexedRequest;

        @Override
        public void index(RagIndexRequest request) {
            this.indexedRequest = request;
        }

        @Override
        public java.util.List<RagSearchResult> search(RagSearchRequest request) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<RagSearchResult> searchByObject(
                RagSearchRequest request,
                String objectType,
                String objectId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return java.util.Optional.empty();
        }
    }
}
