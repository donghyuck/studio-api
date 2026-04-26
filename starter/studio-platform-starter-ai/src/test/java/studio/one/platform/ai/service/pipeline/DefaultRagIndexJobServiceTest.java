package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;

class DefaultRagIndexJobServiceTest {

    private final InMemoryRagIndexJobRepository repository = new InMemoryRagIndexJobRepository();

    @Test
    void tracksSuccessfulRawTextIndexLifecycle() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of("objectType", "attachment", "objectId", "42"))));

        RagIndexJob completed = service.startJob(job.jobId());

        assertThat(completed.status()).isEqualTo(RagIndexJobStatus.SUCCEEDED);
        assertThat(completed.currentStep()).isEqualTo(RagIndexJobStep.COMPLETED);
        assertThat(completed.chunkCount()).isEqualTo(2);
        assertThat(completed.embeddedCount()).isEqualTo(2);
        assertThat(completed.indexedCount()).isEqualTo(2);
        assertThat(service.getLogs(job.jobId()))
                .extracting(log -> log.level())
                .contains(RagIndexJobLogLevel.INFO);
    }

    @Test
    void recordsFailureWithoutThrowingFromStartJob() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new FailingPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));

        RagIndexJob failed = service.startJob(job.jobId());

        assertThat(failed.status()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(failed.currentStep()).isEqualTo(RagIndexJobStep.EMBEDDING);
        assertThat(failed.errorMessage()).contains("boom");
        assertThat(service.getLogs(job.jobId()))
                .anySatisfy(log -> {
                    assertThat(log.level()).isEqualTo(RagIndexJobLogLevel.ERROR);
                    assertThat(log.detail()).contains("boom");
                });
    }

    @Test
    void retryResetsCountsAndRerunsStoredRequest() {
        CountingPipeline pipeline = new CountingPipeline();
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, pipeline);
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));

        service.startJob(job.jobId());
        RagIndexJob retried = service.retryJob(job.jobId());

        assertThat(pipeline.calls).isEqualTo(2);
        assertThat(retried.status()).isEqualTo(RagIndexJobStatus.SUCCEEDED);
        assertThat(retried.chunkCount()).isEqualTo(1);
    }

    @Test
    void retryRejectsActiveJob() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));

        assertThatThrownBy(() -> service.retryJob(job.jobId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be retried");
    }

    private static class SuccessfulPipeline extends BasePipeline {

        @Override
        public void index(RagIndexRequest request, RagIndexProgressListener listener) {
            listener.onStep(RagIndexJobStep.CHUNKING);
            listener.onChunkCount(2);
            listener.onStep(RagIndexJobStep.EMBEDDING);
            listener.onEmbeddedCount(2);
            listener.onStep(RagIndexJobStep.INDEXING);
            listener.onIndexedCount(2);
        }
    }

    private static class FailingPipeline extends BasePipeline {

        @Override
        public void index(RagIndexRequest request, RagIndexProgressListener listener) {
            listener.onStep(RagIndexJobStep.EMBEDDING);
            throw new IllegalStateException("boom");
        }
    }

    private static class CountingPipeline extends BasePipeline {

        private int calls;

        @Override
        public void index(RagIndexRequest request, RagIndexProgressListener listener) {
            calls++;
            listener.onStep(RagIndexJobStep.CHUNKING);
            listener.onChunkCount(1);
        }
    }

    private abstract static class BasePipeline implements RagPipelineService {

        @Override
        public void index(RagIndexRequest request) {
        }

        @Override
        public List<RagSearchResult> search(RagSearchRequest request) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
            return List.of();
        }

        @Override
        public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
            return List.of();
        }

        @Override
        public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
            return Optional.empty();
        }
    }
}
