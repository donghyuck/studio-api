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
import studio.one.platform.ai.core.rag.RagIndexJobSourceRequest;
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

    @Test
    void retryRejectsWhenStoredRequestIsNoLongerAvailable() {
        InMemoryRagIndexJobRepository persistedOnlyRepository = new InMemoryRagIndexJobRepository();
        RagIndexJob failed = RagIndexJob.pending(
                "job-1",
                "attachment",
                "42",
                "doc-1",
                "raw",
                java.time.Instant.parse("2026-04-26T00:00:00Z"))
                .withStatus(RagIndexJobStatus.FAILED, RagIndexJobStep.EMBEDDING, "boom",
                        java.time.Instant.parse("2026-04-26T00:00:01Z"));
        persistedOnlyRepository.save(failed);
        DefaultRagIndexJobService service =
                new DefaultRagIndexJobService(persistedOnlyRepository, new SuccessfulPipeline());

        assertThatThrownBy(() -> service.retryJob("job-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request is no longer available");

        assertThat(service.getJob("job-1").orElseThrow().status()).isEqualTo(RagIndexJobStatus.FAILED);
    }

    @Test
    void cancelsActiveJobAndRecordsLog() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));
        repository.updateStatus(job.jobId(), RagIndexJobStatus.RUNNING, RagIndexJobStep.EMBEDDING, null);

        RagIndexJob cancelled = service.cancelJob(job.jobId());

        assertThat(cancelled.status()).isEqualTo(RagIndexJobStatus.CANCELLED);
        assertThat(cancelled.currentStep()).isEqualTo(RagIndexJobStep.EMBEDDING);
        assertThat(cancelled.errorMessage()).isEqualTo("RAG index job cancelled");
        assertThat(service.getLogs(job.jobId()))
                .anySatisfy(log -> assertThat(log.code()).isEqualTo(
                        studio.one.platform.ai.core.rag.RagIndexJobLogCode.JOB_CANCELLED));
    }

    @Test
    void cancelRejectsTerminalJob() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));
        service.startJob(job.jobId());

        assertThatThrownBy(() -> service.cancelJob(job.jobId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can only be cancelled");
    }

    @Test
    void cancelledJobIgnoresLateCompletionCallbacks() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));
        repository.updateStatus(job.jobId(), RagIndexJobStatus.RUNNING, RagIndexJobStep.INDEXING, null);
        service.cancelJob(job.jobId());

        RagIndexProgressListener listener = service.progressListener(job.jobId());
        listener.onIndexedCount(9);
        listener.onCompleted();

        RagIndexJob current = service.getJob(job.jobId()).orElseThrow();
        assertThat(current.status()).isEqualTo(RagIndexJobStatus.CANCELLED);
        assertThat(current.indexedCount()).isZero();
    }

    @Test
    void startJobStopsBeforePipelineWhenJobIsCancelledAfterStartTransition() {
        CancellingOnStartRepository cancellingRepository = new CancellingOnStartRepository();
        CountingPipeline pipeline = new CountingPipeline();
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(cancellingRepository, pipeline);
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "raw",
                false,
                new RagIndexRequest("doc-1", "content", Map.of())));

        RagIndexJob cancelled = service.startJob(job.jobId());

        assertThat(cancelled.status()).isEqualTo(RagIndexJobStatus.CANCELLED);
        assertThat(pipeline.calls).isZero();
    }

    @Test
    void delegatesNonTextSourceToMatchingExecutor() {
        CapturingSourceExecutor executor = new CapturingSourceExecutor();
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(
                repository,
                new SuccessfulPipeline(),
                List.of(executor));
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null),
                new RagIndexJobSourceRequest(Map.of("attachmentId", "42"), List.of("alpha"), true));

        RagIndexJob completed = service.startJob(job.jobId());

        assertThat(completed.status()).isEqualTo(RagIndexJobStatus.SUCCEEDED);
        assertThat(completed.chunkCount()).isEqualTo(3);
        assertThat(executor.sourceRequest.metadata()).containsEntry("attachmentId", "42");
        assertThat(executor.sourceRequest.keywords()).containsExactly("alpha");
    }

    @Test
    void marksUnsupportedNonTextSourceAsFailed() {
        DefaultRagIndexJobService service = new DefaultRagIndexJobService(repository, new SuccessfulPipeline());
        RagIndexJob job = service.createJob(new RagIndexJobCreateRequest(
                "attachment",
                "42",
                "doc-1",
                "attachment",
                false,
                null));

        RagIndexJob failed = service.startJob(job.jobId());

        assertThat(failed.status()).isEqualTo(RagIndexJobStatus.FAILED);
        assertThat(service.getLogs(job.jobId()))
                .anySatisfy(log -> assertThat(log.code()).isEqualTo(
                        studio.one.platform.ai.core.rag.RagIndexJobLogCode.SOURCE_UNSUPPORTED));
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

    private static class CapturingSourceExecutor implements RagIndexJobSourceExecutor {

        private RagIndexJobCreateRequest request;
        private RagIndexJobSourceRequest sourceRequest;

        @Override
        public boolean supports(RagIndexJobCreateRequest request, RagIndexJobSourceRequest sourceRequest) {
            return "attachment".equals(request.sourceType());
        }

        @Override
        public void execute(
                RagIndexJob job,
                RagIndexJobCreateRequest request,
                RagIndexJobSourceRequest sourceRequest,
                RagIndexProgressListener listener) {
            this.request = request;
            this.sourceRequest = sourceRequest;
            listener.onStep(RagIndexJobStep.CHUNKING);
            listener.onChunkCount(3);
            listener.onStep(RagIndexJobStep.EMBEDDING);
            listener.onEmbeddedCount(3);
            listener.onStep(RagIndexJobStep.INDEXING);
            listener.onIndexedCount(3);
        }
    }

    private static class CancellingOnStartRepository extends InMemoryRagIndexJobRepository {

        @Override
        public RagIndexJob updateStatus(
                String jobId,
                RagIndexJobStatus status,
                RagIndexJobStep currentStep,
                String errorMessage) {
            RagIndexJob updated = super.updateStatus(jobId, status, currentStep, errorMessage);
            if (status == RagIndexJobStatus.RUNNING) {
                return super.updateStatus(
                        jobId,
                        RagIndexJobStatus.CANCELLED,
                        updated.currentStep(),
                        "RAG index job cancelled");
            }
            return updated;
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
