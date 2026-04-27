package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

class InMemoryRagIndexJobRepositoryTest {

    private final InMemoryRagIndexJobRepository repository = new InMemoryRagIndexJobRepository();

    @Test
    void savesListsUpdatesAndFiltersJobs() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.save(RagIndexJob.pending("job-2", "page", "2", "doc-2", "raw",
                Instant.parse("2026-04-26T00:00:01Z")));

        repository.updateStatus("job-1", RagIndexJobStatus.RUNNING, RagIndexJobStep.CHUNKING, null);
        repository.updateCounts("job-1", 2, 1, null, null);

        assertThat(repository.findById("job-1")).get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(RagIndexJobStatus.RUNNING);
                    assertThat(job.currentStep()).isEqualTo(RagIndexJobStep.CHUNKING);
                    assertThat(job.chunkCount()).isEqualTo(2);
                    assertThat(job.embeddedCount()).isEqualTo(1);
                });
        assertThat(repository.findAll(
                        new RagIndexJobFilter(RagIndexJobStatus.RUNNING, "attachment", null, null),
                        new RagIndexJobPageRequest(0, 10))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-1");
    }

    @Test
    void appendsLogsInOrder() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment", Instant.now()));

        repository.appendLog(new RagIndexJobLog(
                "log-1",
                "job-1",
                RagIndexJobLogLevel.INFO,
                RagIndexJobStep.EXTRACTING,
                RagIndexJobLogCode.JOB_STARTED,
                "started",
                null,
                Instant.now()));
        repository.appendLog(new RagIndexJobLog(
                "log-2",
                "job-1",
                RagIndexJobLogLevel.ERROR,
                RagIndexJobStep.EMBEDDING,
                RagIndexJobLogCode.EMBEDDING_FAILED,
                "failed",
                "boom",
                Instant.now()));

        assertThat(repository.findLogs("job-1"))
                .extracting(RagIndexJobLog::logId)
                .containsExactly("log-1", "log-2");
    }

    @Test
    void sortsAndPagesJobsWithStableDefaults() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.save(RagIndexJob.pending("job-2", "attachment", "2", "doc-2", "attachment",
                Instant.parse("2026-04-26T00:00:02Z")));
        repository.save(RagIndexJob.pending("job-3", "attachment", "3", "doc-3", "attachment",
                Instant.parse("2026-04-26T00:00:01Z")));

        assertThat(repository.findAll(RagIndexJobFilter.empty(), RagIndexJobPageRequest.defaults()).jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-2", "job-3", "job-1");

        assertThat(repository.findAll(RagIndexJobFilter.empty(),
                        new RagIndexJobPageRequest(
                                1,
                                1),
                        new RagIndexJobSort(
                                RagIndexJobSort.Field.CREATED_AT,
                                RagIndexJobSort.Direction.DESC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-3");
    }

    @Test
    void sortsByRequestedFieldAndDirection() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "2", "doc-b", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.save(RagIndexJob.pending("job-2", "attachment", "1", "doc-a", "attachment",
                Instant.parse("2026-04-26T00:00:01Z")));

        assertThat(repository.findAll(RagIndexJobFilter.empty(),
                        new RagIndexJobPageRequest(
                                0,
                                10),
                        new RagIndexJobSort(
                                RagIndexJobSort.Field.DOCUMENT_ID,
                                RagIndexJobSort.Direction.ASC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-2", "job-1");
    }

    @Test
    void keepsNullSortValuesLastInDescendingOrder() {
        repository.save(RagIndexJob.pending("job-running", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        RagIndexJob completed = RagIndexJob.pending("job-completed", "attachment", "2", "doc-2", "attachment",
                        Instant.parse("2026-04-26T00:00:01Z"))
                .withStatus(
                        RagIndexJobStatus.SUCCEEDED,
                        RagIndexJobStep.COMPLETED,
                        null,
                        Instant.parse("2026-04-26T00:00:02Z"));
        repository.save(completed);

        assertThat(repository.findAll(RagIndexJobFilter.empty(),
                        new RagIndexJobPageRequest(
                                0,
                                10),
                        new RagIndexJobSort(
                                RagIndexJobSort.Field.FINISHED_AT,
                                RagIndexJobSort.Direction.DESC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-completed", "job-running");
    }

    @Test
    void usesJobIdAsStableTieBreakerAcrossPages() {
        repository.save(RagIndexJob.pending("job-b", "attachment", "1", "same-doc", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.save(RagIndexJob.pending("job-a", "attachment", "2", "same-doc", "attachment",
                Instant.parse("2026-04-26T00:00:01Z")));

        assertThat(repository.findAll(RagIndexJobFilter.empty(),
                        new RagIndexJobPageRequest(0, 1),
                        new RagIndexJobSort(
                                RagIndexJobSort.Field.DOCUMENT_ID,
                                RagIndexJobSort.Direction.ASC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-a");
        assertThat(repository.findAll(RagIndexJobFilter.empty(),
                        new RagIndexJobPageRequest(1, 1),
                        new RagIndexJobSort(
                                RagIndexJobSort.Field.DOCUMENT_ID,
                                RagIndexJobSort.Direction.ASC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-b");
    }

    @Test
    void cancelledJobIsNotOverwrittenByLateStatusOrCountUpdates() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.updateStatus("job-1", RagIndexJobStatus.RUNNING, RagIndexJobStep.INDEXING, null);
        repository.cancelJob("job-1", "cancelled");

        repository.updateCounts("job-1", 9, 9, 9, 9);
        repository.updateStatus("job-1", RagIndexJobStatus.SUCCEEDED, RagIndexJobStep.COMPLETED, null);

        assertThat(repository.findById("job-1")).get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(RagIndexJobStatus.CANCELLED);
                    assertThat(job.currentStep()).isEqualTo(RagIndexJobStep.INDEXING);
                    assertThat(job.chunkCount()).isZero();
                    assertThat(job.indexedCount()).isZero();
                });
    }

    @Test
    void cancelJobOnlyTransitionsActiveJobs() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.updateStatus("job-1", RagIndexJobStatus.SUCCEEDED, RagIndexJobStep.COMPLETED, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.cancelJob("job-1", "cancelled"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can only be cancelled");

        assertThat(repository.findById("job-1")).get()
                .extracting(RagIndexJob::status)
                .isEqualTo(RagIndexJobStatus.SUCCEEDED);
    }

    @Test
    void cancelledJobIgnoresLateNonCancelLogs() {
        repository.save(RagIndexJob.pending("job-1", "attachment", "1", "doc-1", "attachment",
                Instant.parse("2026-04-26T00:00:00Z")));
        repository.cancelJob("job-1", "cancelled");

        repository.appendLog(new RagIndexJobLog(
                "log-completed",
                "job-1",
                RagIndexJobLogLevel.INFO,
                RagIndexJobStep.COMPLETED,
                RagIndexJobLogCode.JOB_COMPLETED,
                "completed",
                null,
                Instant.now()));
        repository.appendLog(new RagIndexJobLog(
                "log-cancelled",
                "job-1",
                RagIndexJobLogLevel.INFO,
                RagIndexJobStep.INDEXING,
                RagIndexJobLogCode.JOB_CANCELLED,
                "cancelled",
                null,
                Instant.now()));

        assertThat(repository.findLogs("job-1"))
                .extracting(RagIndexJobLog::code)
                .containsExactly(RagIndexJobLogCode.JOB_CANCELLED);
    }
}
