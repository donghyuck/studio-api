package studio.one.platform.ai.core.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.service.pipeline.RagIndexJobRepository;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
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
        RagIndexJobSort sort = new RagIndexJobSort(
                RagIndexJobSort.Field.from(" document-id "),
                RagIndexJobSort.Direction.from("asc"));

        assertThat(legacy.offset()).isZero();
        assertThat(legacy.limit()).isEqualTo(50);
        assertThat(sort.field()).isEqualTo(RagIndexJobSort.Field.DOCUMENT_ID);
        assertThat(sort.direction()).isEqualTo(RagIndexJobSort.Direction.ASC);
        assertThat(RagIndexJobSort.Field.from("unknown")).isEqualTo(RagIndexJobSort.Field.CREATED_AT);
    }

    @Test
    void repositorySortOverloadFallsBackToLegacyFindAllByDefault() {
        LegacyRepository repository = new LegacyRepository();
        RagIndexJobPageRequest pageRequest = new RagIndexJobPageRequest(0, 10);

        repository.findAll(RagIndexJobFilter.empty(), pageRequest, RagIndexJobSort.defaults());

        assertThat(repository.usedLegacyFindAll).isTrue();
    }

    @Test
    void serviceSortOverloadFallsBackToLegacyListJobsByDefault() {
        LegacyJobService service = new LegacyJobService();
        RagIndexJobPageRequest pageRequest = new RagIndexJobPageRequest(0, 10);

        service.listJobs(RagIndexJobFilter.empty(), pageRequest, RagIndexJobSort.defaults());

        assertThat(service.usedLegacyListJobs).isTrue();
    }

    @Test
    void pipelineListenerOverloadDelegatesToLegacyIndexByDefault() {
        CapturingRagPipelineService service = new CapturingRagPipelineService();
        RagIndexRequest request = new RagIndexRequest("doc-1", "content", Map.of());

        service.index(request, RagIndexProgressListener.noop());

        assertThat(service.indexedRequest).isSameAs(request);
    }

    private static class LegacyRepository implements RagIndexJobRepository {

        private boolean usedLegacyFindAll;

        @Override
        public RagIndexJob save(RagIndexJob job) {
            return job;
        }

        @Override
        public java.util.Optional<RagIndexJob> findById(String jobId) {
            return java.util.Optional.empty();
        }

        @Override
        public RagIndexJobPage findAll(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
            usedLegacyFindAll = true;
            return new RagIndexJobPage(List.of(), 0, pageable.offset(), pageable.limit());
        }

        @Override
        public RagIndexJob updateStatus(
                String jobId,
                RagIndexJobStatus status,
                RagIndexJobStep currentStep,
                String errorMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJob updateCounts(
                String jobId,
                Integer chunkCount,
                Integer embeddedCount,
                Integer indexedCount,
                Integer warningCount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJobLog appendLog(RagIndexJobLog log) {
            return log;
        }

        @Override
        public java.util.List<RagIndexJobLog> findLogs(String jobId) {
            return java.util.List.of();
        }
    }

    private static class LegacyJobService implements RagIndexJobService {

        private boolean usedLegacyListJobs;

        @Override
        public RagIndexJob createJob(RagIndexJobCreateRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJob startJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RagIndexJob retryJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<RagIndexJob> getJob(String jobId) {
            return java.util.Optional.empty();
        }

        @Override
        public RagIndexJobPage listJobs(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
            usedLegacyListJobs = true;
            return new RagIndexJobPage(List.of(), 0, pageable.offset(), pageable.limit());
        }

        @Override
        public java.util.List<RagIndexJobLog> getLogs(String jobId) {
            return java.util.List.of();
        }

        @Override
        public RagIndexProgressListener progressListener(String jobId) {
            return RagIndexProgressListener.noop();
        }
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
