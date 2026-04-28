package studio.one.platform.ai.service.pipeline;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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

public class JdbcRagIndexJobRepository implements RagIndexJobRepository {

    private static final RowMapper<RagIndexJob> JOB_ROW_MAPPER = (rs, rowNum) -> new RagIndexJob(
            rs.getString("job_id"),
            rs.getString("object_type"),
            rs.getString("object_id"),
            rs.getString("document_id"),
            rs.getString("source_type"),
            rs.getString("source_name"),
            enumValue(RagIndexJobStatus.class, rs.getString("status"), RagIndexJobStatus.PENDING),
            enumValue(RagIndexJobStep.class, rs.getString("current_step"), null),
            rs.getInt("chunk_count"),
            rs.getInt("embedded_count"),
            rs.getInt("indexed_count"),
            rs.getInt("warning_count"),
            rs.getString("error_message"),
            instant(rs, "created_at"),
            instant(rs, "started_at"),
            instant(rs, "finished_at"),
            nullableLong(rs, "duration_ms"));

    private static final RowMapper<RagIndexJobLog> LOG_ROW_MAPPER = (rs, rowNum) -> new RagIndexJobLog(
            rs.getString("log_id"),
            rs.getString("job_id"),
            enumValue(RagIndexJobLogLevel.class, rs.getString("log_level"), RagIndexJobLogLevel.INFO),
            enumValue(RagIndexJobStep.class, rs.getString("step"), null),
            enumValue(RagIndexJobLogCode.class, rs.getString("code"), RagIndexJobLogCode.UNKNOWN_ERROR),
            rs.getString("message"),
            rs.getString("detail"),
            instant(rs, "created_at"));

    private final NamedParameterJdbcTemplate template;

    public JdbcRagIndexJobRepository(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public RagIndexJob save(RagIndexJob job) {
        if (findById(job.jobId()).isPresent()) {
            template.update("""
                    UPDATE tb_ai_rag_index_job
                       SET object_type = :objectType,
                           object_id = :objectId,
                           document_id = :documentId,
                           source_type = :sourceType,
                           source_name = :sourceName,
                           status = :status,
                           current_step = :currentStep,
                           chunk_count = :chunkCount,
                           embedded_count = :embeddedCount,
                           indexed_count = :indexedCount,
                           warning_count = :warningCount,
                           error_message = :errorMessage,
                           created_at = :createdAt,
                           started_at = :startedAt,
                           finished_at = :finishedAt,
                           duration_ms = :durationMs
                     WHERE job_id = :jobId
                    """, jobParameters(job));
            return job;
        }
        template.update("""
                INSERT INTO tb_ai_rag_index_job(
                    job_id, object_type, object_id, document_id, source_type, source_name,
                    status, current_step, chunk_count, embedded_count, indexed_count,
                    warning_count, error_message, created_at, started_at, finished_at, duration_ms)
                VALUES (
                    :jobId, :objectType, :objectId, :documentId, :sourceType, :sourceName,
                    :status, :currentStep, :chunkCount, :embeddedCount, :indexedCount,
                    :warningCount, :errorMessage, :createdAt, :startedAt, :finishedAt, :durationMs)
                """, jobParameters(job));
        return job;
    }

    @Override
    public Optional<RagIndexJob> findById(String jobId) {
        List<RagIndexJob> jobs = template.query("""
                SELECT *
                  FROM tb_ai_rag_index_job
                 WHERE job_id = :jobId
                """, new MapSqlParameterSource("jobId", jobId), JOB_ROW_MAPPER);
        return jobs.stream().findFirst();
    }

    @Override
    public RagIndexJobPage findAll(RagIndexJobFilter filter, RagIndexJobPageRequest pageable) {
        return findAll(filter, pageable, RagIndexJobSort.defaults());
    }

    @Override
    public RagIndexJobPage findAll(
            RagIndexJobFilter filter,
            RagIndexJobPageRequest pageable,
            RagIndexJobSort sort) {
        RagIndexJobFilter effectiveFilter = filter == null ? RagIndexJobFilter.empty() : filter;
        RagIndexJobPageRequest effectivePageable = pageable == null ? RagIndexJobPageRequest.defaults() : pageable;
        RagIndexJobSort effectiveSort = sort == null ? RagIndexJobSort.defaults() : sort;
        QueryParts query = queryParts(effectiveFilter);
        long total = Optional.ofNullable(template.queryForObject(
                "SELECT COUNT(*) FROM tb_ai_rag_index_job" + query.whereClause(),
                query.params(),
                Long.class)).orElse(0L);
        MapSqlParameterSource params = query.params()
                .addValue("limit", effectivePageable.limit())
                .addValue("offset", effectivePageable.offset());
        List<RagIndexJob> jobs = template.query("""
                SELECT *
                  FROM tb_ai_rag_index_job
                """
                + query.whereClause()
                + " ORDER BY " + orderBy(effectiveSort)
                + " LIMIT :limit OFFSET :offset",
                params,
                JOB_ROW_MAPPER);
        return new RagIndexJobPage(jobs, total, effectivePageable.offset(), effectivePageable.limit());
    }

    @Override
    public RagIndexJob updateStatus(
            String jobId,
            RagIndexJobStatus status,
            RagIndexJobStep currentStep,
            String errorMessage) {
        RagIndexJob existing = requireJob(jobId);
        if (existing.status() == RagIndexJobStatus.CANCELLED && status != RagIndexJobStatus.CANCELLED) {
            return existing;
        }
        RagIndexJobStep nextStep = currentStep == null ? existing.currentStep() : currentStep;
        if ((status == RagIndexJobStatus.SUCCEEDED || status == RagIndexJobStatus.WARNING) && nextStep == null) {
            nextStep = RagIndexJobStep.COMPLETED;
        }
        RagIndexJob updated = existing.withStatus(status, nextStep, errorMessage, Instant.now());
        save(updated);
        return updated;
    }

    @Override
    public RagIndexJob cancelJob(String jobId, String errorMessage) {
        RagIndexJob existing = requireJob(jobId);
        if (existing.status() != RagIndexJobStatus.PENDING && existing.status() != RagIndexJobStatus.RUNNING) {
            throw new IllegalStateException("RAG index job can only be cancelled while active: " + jobId);
        }
        RagIndexJob cancelled = existing.withStatus(
                RagIndexJobStatus.CANCELLED,
                existing.currentStep(),
                errorMessage,
                Instant.now());
        save(cancelled);
        return cancelled;
    }

    @Override
    public RagIndexJob updateCounts(
            String jobId,
            Integer chunkCount,
            Integer embeddedCount,
            Integer indexedCount,
            Integer warningCount) {
        RagIndexJob existing = requireJob(jobId);
        if (existing.status() == RagIndexJobStatus.CANCELLED) {
            return existing;
        }
        RagIndexJob updated = existing.withCounts(chunkCount, embeddedCount, indexedCount, warningCount);
        save(updated);
        return updated;
    }

    @Override
    public RagIndexJobLog appendLog(RagIndexJobLog log) {
        Optional<RagIndexJob> job = findById(log.jobId());
        if (job.filter(value -> value.status() == RagIndexJobStatus.CANCELLED
                && log.code() != RagIndexJobLogCode.JOB_CANCELLED).isPresent()) {
            return log;
        }
        template.update("""
                INSERT INTO tb_ai_rag_index_job_log(
                    log_id, job_id, log_level, step, code, message, detail, created_at)
                VALUES (
                    :logId, :jobId, :logLevel, :step, :code, :message, :detail, :createdAt)
                """, logParameters(log));
        return log;
    }

    @Override
    public List<RagIndexJobLog> findLogs(String jobId) {
        return template.query("""
                SELECT *
                  FROM tb_ai_rag_index_job_log
                 WHERE job_id = :jobId
                 ORDER BY created_at ASC, log_id ASC
                """, new MapSqlParameterSource("jobId", jobId), LOG_ROW_MAPPER);
    }

    private RagIndexJob requireJob(String jobId) {
        return findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("RAG index job not found: " + jobId));
    }

    private MapSqlParameterSource jobParameters(RagIndexJob job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId())
                .addValue("objectType", job.objectType())
                .addValue("objectId", job.objectId())
                .addValue("documentId", job.documentId())
                .addValue("sourceType", job.sourceType())
                .addValue("sourceName", job.sourceName())
                .addValue("status", job.status().name())
                .addValue("currentStep", enumName(job.currentStep()))
                .addValue("chunkCount", job.chunkCount())
                .addValue("embeddedCount", job.embeddedCount())
                .addValue("indexedCount", job.indexedCount())
                .addValue("warningCount", job.warningCount())
                .addValue("errorMessage", job.errorMessage())
                .addValue("createdAt", timestamp(job.createdAt()))
                .addValue("startedAt", timestamp(job.startedAt()))
                .addValue("finishedAt", timestamp(job.finishedAt()))
                .addValue("durationMs", job.durationMs());
    }

    private MapSqlParameterSource logParameters(RagIndexJobLog log) {
        return new MapSqlParameterSource()
                .addValue("logId", log.logId())
                .addValue("jobId", log.jobId())
                .addValue("logLevel", log.level().name())
                .addValue("step", enumName(log.step()))
                .addValue("code", log.code().name())
                .addValue("message", log.message())
                .addValue("detail", log.detail())
                .addValue("createdAt", timestamp(log.createdAt()));
    }

    private QueryParts queryParts(RagIndexJobFilter filter) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> predicates = new ArrayList<>();
        if (filter.status() != null) {
            predicates.add("status = :status");
            params.addValue("status", filter.status().name());
        }
        addTextPredicate(predicates, params, "object_type", "objectType", filter.objectType());
        addTextPredicate(predicates, params, "object_id", "objectId", filter.objectId());
        addTextPredicate(predicates, params, "document_id", "documentId", filter.documentId());
        String whereClause = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        return new QueryParts(whereClause, params);
    }

    private void addTextPredicate(
            List<String> predicates,
            MapSqlParameterSource params,
            String column,
            String parameter,
            String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(column + " = :" + parameter);
            params.addValue(parameter, value);
        }
    }

    private String orderBy(RagIndexJobSort sort) {
        String column = switch (sort.field()) {
            case STARTED_AT -> "started_at";
            case FINISHED_AT -> "finished_at";
            case STATUS -> "status";
            case CURRENT_STEP -> "current_step";
            case OBJECT_TYPE -> "object_type";
            case OBJECT_ID -> "object_id";
            case DOCUMENT_ID -> "document_id";
            case SOURCE_TYPE -> "source_type";
            case DURATION_MS -> "duration_ms";
            case CREATED_AT -> "created_at";
        };
        String direction = sort.direction() == RagIndexJobSort.Direction.ASC ? "ASC" : "DESC";
        return column + " " + direction + ", job_id ASC";
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private record QueryParts(String whereClause, MapSqlParameterSource params) {
    }
}
