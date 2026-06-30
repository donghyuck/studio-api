package studio.one.platform.ai.web.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.ai.web.dto.RagRetrievalEvaluationJobDto;

public class JdbcRagRetrievalEvaluationJobStore implements RagRetrievalEvaluationJobStore {
    private static final String INTERRUPTED_MESSAGE = "Server restarted before evaluation job completed";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<RagRetrievalEvaluationJobDto> rowMapper = this::map;

    public JdbcRagRetrievalEvaluationJobStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        failInterruptedJobs();
    }

    @Override
    public RagRetrievalEvaluationJobDto save(RagRetrievalEvaluationJobDto job) {
        jdbcTemplate.update("DELETE FROM tb_ai_rag_retrieval_evaluation_job WHERE job_id = :jobId",
                Map.of("jobId", job.jobId()));
        String sql = """
                INSERT INTO tb_ai_rag_retrieval_evaluation_job (
                    job_id, status, created_at, started_at, completed_at,
                    total_questions, completed_questions, total_strategies, completed_strategies,
                    current_strategy, current_question, run_id, error_message
                ) VALUES (
                    :jobId, :status, :createdAt, :startedAt, :completedAt,
                    :totalQuestions, :completedQuestions, :totalStrategies, :completedStrategies,
                    :currentStrategy, :currentQuestion, :runId, :errorMessage
                )
                """;
        jdbcTemplate.update(sql, params(job));
        return job;
    }

    @Override
    public Optional<RagRetrievalEvaluationJobDto> find(String jobId) {
        String sql = """
                SELECT *
                FROM tb_ai_rag_retrieval_evaluation_job
                WHERE job_id = :jobId
                """;
        return jdbcTemplate.query(sql, Map.of("jobId", jobId), rowMapper).stream().findFirst();
    }

    @Override
    public List<RagRetrievalEvaluationJobDto> list() {
        String sql = """
                SELECT *
                FROM tb_ai_rag_retrieval_evaluation_job
                ORDER BY created_at DESC, job_id DESC
                LIMIT 100
                """;
        return jdbcTemplate.query(sql, rowMapper);
    }

    private void failInterruptedJobs() {
        String sql = """
                UPDATE tb_ai_rag_retrieval_evaluation_job
                SET status = 'FAILED',
                    completed_at = :completedAt,
                    error_message = :errorMessage
                WHERE status IN ('PENDING', 'RUNNING')
                """;
        jdbcTemplate.update(sql, Map.of(
                "completedAt", timestamp(Instant.now()),
                "errorMessage", INTERRUPTED_MESSAGE));
    }

    private MapSqlParameterSource params(RagRetrievalEvaluationJobDto job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId())
                .addValue("status", job.status())
                .addValue("createdAt", timestamp(job.createdAt()))
                .addValue("startedAt", timestamp(job.startedAt()))
                .addValue("completedAt", timestamp(job.completedAt()))
                .addValue("totalQuestions", job.totalQuestions())
                .addValue("completedQuestions", job.completedQuestions())
                .addValue("totalStrategies", job.totalStrategies())
                .addValue("completedStrategies", job.completedStrategies())
                .addValue("currentStrategy", job.currentStrategy())
                .addValue("currentQuestion", job.currentQuestion())
                .addValue("runId", job.runId())
                .addValue("errorMessage", job.errorMessage());
    }

    private RagRetrievalEvaluationJobDto map(ResultSet rs, int rowNum) throws SQLException {
        return new RagRetrievalEvaluationJobDto(
                rs.getString("job_id"),
                rs.getString("status"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")),
                rs.getInt("total_questions"),
                rs.getInt("completed_questions"),
                rs.getInt("total_strategies"),
                rs.getInt("completed_strategies"),
                rs.getString("current_strategy"),
                rs.getString("current_question"),
                rs.getString("run_id"),
                rs.getString("error_message"));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
