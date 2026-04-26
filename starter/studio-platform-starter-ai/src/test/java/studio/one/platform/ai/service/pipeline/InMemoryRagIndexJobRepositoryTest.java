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
                                1,
                                RagIndexJobPageRequest.Sort.CREATED_AT,
                                RagIndexJobPageRequest.Direction.DESC))
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
                                10,
                                RagIndexJobPageRequest.Sort.DOCUMENT_ID,
                                RagIndexJobPageRequest.Direction.ASC))
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
                                10,
                                RagIndexJobPageRequest.Sort.FINISHED_AT,
                                RagIndexJobPageRequest.Direction.DESC))
                .jobs())
                .extracting(RagIndexJob::jobId)
                .containsExactly("job-completed", "job-running");
    }
}
