package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobFilter;
import studio.one.platform.ai.core.rag.RagIndexJobLog;
import studio.one.platform.ai.core.rag.RagIndexJobLogCode;
import studio.one.platform.ai.core.rag.RagIndexJobLogLevel;
import studio.one.platform.ai.core.rag.RagIndexJobPage;
import studio.one.platform.ai.core.rag.RagIndexJobPageRequest;
import studio.one.platform.ai.core.rag.RagIndexJobSort;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexJobStep;

class JdbcRagIndexJobRepositoryTest {

    private EmbeddedDatabase database;
    private JdbcRagIndexJobRepository repository;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
        createSchema(jdbcTemplate);
        repository = new JdbcRagIndexJobRepository(new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void savesFindsFiltersAndSortsJobs() {
        RagIndexJob older = pending("job-1", "attachment", "42", "doc-1")
                .withStatus(RagIndexJobStatus.SUCCEEDED, RagIndexJobStep.COMPLETED, null,
                        Instant.parse("2026-04-26T00:00:10Z"));
        RagIndexJob newer = pending("job-2", "attachment", "43", "doc-2")
                .withStatus(RagIndexJobStatus.FAILED, RagIndexJobStep.EMBEDDING, "failed",
                        Instant.parse("2026-04-26T00:00:20Z"));
        repository.save(older);
        repository.save(newer);

        RagIndexJobPage page = repository.findAll(
                new RagIndexJobFilter(null, "attachment", null, null),
                new RagIndexJobPageRequest(0, 10),
                new RagIndexJobSort(RagIndexJobSort.Field.DOCUMENT_ID, RagIndexJobSort.Direction.DESC));

        assertThat(repository.findById("job-1")).contains(older);
        assertThat(repository.findById("job-1").orElseThrow().sourceName()).isEqualTo("doc-1");
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.jobs()).extracting(RagIndexJob::jobId).containsExactly("job-2", "job-1");
    }

    @Test
    void updatesStatusCountsCancelAndLogs() {
        repository.save(pending("job-1", "attachment", "42", "doc-1"));

        RagIndexJob running = repository.updateStatus("job-1", RagIndexJobStatus.RUNNING, RagIndexJobStep.CHUNKING, null);
        RagIndexJob counted = repository.updateCounts("job-1", 3, 2, 1, 1);
        RagIndexJobLog log = repository.appendLog(new RagIndexJobLog(
                "log-1",
                "job-1",
                RagIndexJobLogLevel.WARN,
                RagIndexJobStep.CHUNKING,
                RagIndexJobLogCode.PARTIAL_PARSE,
                "partial",
                "detail",
                Instant.parse("2026-04-26T00:00:01Z")));
        RagIndexJob cancelled = repository.cancelJob("job-1", "cancelled");
        repository.appendLog(new RagIndexJobLog(
                "late-log",
                "job-1",
                RagIndexJobLogLevel.INFO,
                RagIndexJobStep.INDEXING,
                RagIndexJobLogCode.STEP_CHANGED,
                "late",
                null,
                Instant.parse("2026-04-26T00:00:02Z")));

        assertThat(running.status()).isEqualTo(RagIndexJobStatus.RUNNING);
        assertThat(counted.chunkCount()).isEqualTo(3);
        assertThat(log.logId()).isEqualTo("log-1");
        assertThat(cancelled.status()).isEqualTo(RagIndexJobStatus.CANCELLED);
        assertThat(repository.findLogs("job-1")).extracting(RagIndexJobLog::logId).containsExactly("log-1");
        assertThat(repository.updateCounts("job-1", 9, 9, 9, 9).chunkCount()).isEqualTo(3);
    }

    @Test
    void rejectsCancellingTerminalJob() {
        repository.save(pending("job-1", "attachment", "42", "doc-1")
                .withStatus(RagIndexJobStatus.SUCCEEDED, RagIndexJobStep.COMPLETED, null, Instant.now()));

        assertThatThrownBy(() -> repository.cancelJob("job-1", "cancelled"))
                .isInstanceOf(IllegalStateException.class);
    }

    private RagIndexJob pending(String jobId, String objectType, String objectId, String documentId) {
        return RagIndexJob.pending(
                jobId,
                objectType,
                objectId,
                documentId,
                "attachment",
                documentId,
                Instant.parse("2026-04-26T00:00:00Z"));
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        List.of(
                """
                CREATE TABLE tb_ai_rag_index_job (
                  job_id VARCHAR(100) PRIMARY KEY,
                  object_type VARCHAR(100),
                  object_id VARCHAR(150),
                  document_id VARCHAR(200),
                  source_type VARCHAR(100),
                  source_name VARCHAR(300),
                  status VARCHAR(30) NOT NULL,
                  current_step VARCHAR(30),
                  chunk_count INT NOT NULL DEFAULT 0,
                  embedded_count INT NOT NULL DEFAULT 0,
                  indexed_count INT NOT NULL DEFAULT 0,
                  warning_count INT NOT NULL DEFAULT 0,
                  error_message CLOB,
                  created_at TIMESTAMP NOT NULL,
                  started_at TIMESTAMP,
                  finished_at TIMESTAMP,
                  duration_ms BIGINT
                )
                """,
                """
                CREATE TABLE tb_ai_rag_index_job_log (
                  log_id VARCHAR(100) PRIMARY KEY,
                  job_id VARCHAR(100) NOT NULL,
                  log_level VARCHAR(20) NOT NULL,
                  step VARCHAR(30),
                  code VARCHAR(100) NOT NULL,
                  message CLOB,
                  detail CLOB,
                  created_at TIMESTAMP NOT NULL
                )
                """)
                .forEach(jdbcTemplate::execute);
    }
}
