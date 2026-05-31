package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;

public class JdbcSkillGraphBatchJobStore implements SkillGraphBatchJobStore {

    private static final String INSERT_SQL = """
            insert into tb_skillgraph_batch_job (
                job_id, job_type, status, total_count, requested_count, processed_count, result_count,
                failed_count, skipped_count, embedding_provider, embedding_model, embedding_dimension,
                request_snapshot, error_message, created_by, created_at, started_at, updated_at, completed_at
            ) values (
                :jobId, :jobType, :status, :totalCount, :requestedCount, :processedCount, :resultCount,
                :failedCount, :skippedCount, :embeddingProvider, :embeddingModel, :embeddingDimension,
                :requestSnapshot, :errorMessage, :createdBy, :createdAt, :startedAt, :updatedAt, :completedAt
            )
            """;

    private static final String UPDATE_SQL = """
            update tb_skillgraph_batch_job
               set job_type = :jobType,
                   status = :status,
                   total_count = :totalCount,
                   requested_count = :requestedCount,
                   processed_count = :processedCount,
                   result_count = :resultCount,
                   failed_count = :failedCount,
                   skipped_count = :skippedCount,
                   embedding_provider = :embeddingProvider,
                   embedding_model = :embeddingModel,
                   embedding_dimension = :embeddingDimension,
                   request_snapshot = :requestSnapshot,
                   error_message = :errorMessage,
                   created_by = :createdBy,
                   created_at = :createdAt,
                   started_at = :startedAt,
                   updated_at = :updatedAt,
                   completed_at = :completedAt
             where job_id = :jobId
            """;

    private static final String FIND_BY_ID_SQL = """
            select job_id, job_type, status, total_count, requested_count, processed_count, result_count,
                   failed_count, skipped_count, embedding_provider, embedding_model, embedding_dimension,
                   request_snapshot, error_message, created_by, created_at, started_at, updated_at, completed_at
              from tb_skillgraph_batch_job
             where job_id = :jobId
            """;

    private static final String FIND_SQL = """
            select job_id, job_type, status, total_count, requested_count, processed_count, result_count,
                   failed_count, skipped_count, embedding_provider, embedding_model, embedding_dimension,
                   request_snapshot, error_message, created_by, created_at, started_at, updated_at, completed_at
              from tb_skillgraph_batch_job
             where (:jobType is null or job_type = :jobType)
               and (:status is null or status = :status)
            """;

    private static final String COUNT_SQL = """
            select count(*)
              from tb_skillgraph_batch_job
             where (:jobType is null or job_type = :jobType)
               and (:status is null or status = :status)
            """;

    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("jobId", "job_id"),
            Map.entry("jobType", "job_type"),
            Map.entry("status", "status"),
            Map.entry("totalCount", "total_count"),
            Map.entry("requestedCount", "requested_count"),
            Map.entry("processedCount", "processed_count"),
            Map.entry("resultCount", "result_count"),
            Map.entry("failedCount", "failed_count"),
            Map.entry("skippedCount", "skipped_count"),
            Map.entry("createdAt", "created_at"),
            Map.entry("startedAt", "started_at"),
            Map.entry("updatedAt", "updated_at"),
            Map.entry("completedAt", "completed_at"));

    private static final RowMapper<SkillGraphBatchJob> ROW_MAPPER = (rs, rowNum) -> new SkillGraphBatchJob(
            rs.getString("job_id"),
            SkillGraphBatchJobType.valueOf(rs.getString("job_type")),
            SkillGraphBatchJobStatus.valueOf(rs.getString("status")),
            rs.getLong("total_count"),
            rs.getLong("requested_count"),
            rs.getLong("processed_count"),
            rs.getLong("result_count"),
            rs.getLong("failed_count"),
            rs.getLong("skipped_count"),
            rs.getString("embedding_provider"),
            rs.getString("embedding_model"),
            rs.getInt("embedding_dimension"),
            rs.getString("request_snapshot"),
            rs.getString("error_message"),
            rs.getString("created_by"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("started_at")),
            toInstant(rs.getTimestamp("updated_at")),
            toInstant(rs.getTimestamp("completed_at")));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSkillGraphBatchJobStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SkillGraphBatchJob save(SkillGraphBatchJob job) {
        MapSqlParameterSource params = params(job);
        int updated = jdbcTemplate.update(UPDATE_SQL, params);
        if (updated == 0) {
            jdbcTemplate.update(INSERT_SQL, params);
        }
        return job;
    }

    @Override
    public Optional<SkillGraphBatchJob> findById(String jobId) {
        return jdbcTemplate.query(FIND_BY_ID_SQL, Map.of("jobId", jobId), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public Page<SkillGraphBatchJob> search(
            SkillGraphBatchJobType jobType,
            SkillGraphBatchJobStatus status,
            Pageable pageable) {
        int pageIndex = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());
        int offset = pageIndex * pageSize;
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Order.desc("createdAt"));
        Map<String, Object> sqlParams = new HashMap<>();
        sqlParams.put("jobType", jobType == null ? null : jobType.name());
        sqlParams.put("status", status == null ? null : status.name());
        sqlParams.put("limit", pageSize);
        sqlParams.put("offset", offset);
        String orderedSql = FIND_SQL + buildOrderByClause(sort);
        List<SkillGraphBatchJob> content = jdbcTemplate.query(orderedSql + " limit :limit offset :offset",
                sqlParams,
                ROW_MAPPER);
        Long total = jdbcTemplate.queryForObject(COUNT_SQL, sqlParams, Long.class);
        return new PageImpl<>(content, PageRequest.of(pageIndex, pageSize, sort), total == null ? 0 : total);
    }

    private MapSqlParameterSource params(SkillGraphBatchJob job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId())
                .addValue("jobType", job.jobType().name())
                .addValue("status", job.status().name())
                .addValue("totalCount", job.totalCount())
                .addValue("requestedCount", job.requestedCount())
                .addValue("processedCount", job.processedCount())
                .addValue("resultCount", job.resultCount())
                .addValue("failedCount", job.failedCount())
                .addValue("skippedCount", job.skippedCount())
                .addValue("embeddingProvider", job.embeddingProvider())
                .addValue("embeddingModel", job.embeddingModel())
                .addValue("embeddingDimension", job.embeddingDimension())
                .addValue("requestSnapshot", job.requestSnapshot())
                .addValue("errorMessage", job.errorMessage())
                .addValue("createdBy", job.createdBy())
                .addValue("createdAt", timestamp(job.createdAt()))
                .addValue("startedAt", timestamp(job.startedAt()))
                .addValue("updatedAt", timestamp(job.updatedAt()))
                .addValue("completedAt", timestamp(job.completedAt()));
    }

    private String buildOrderByClause(Sort sort) {
        StringBuilder orderBy = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                continue;
            }
            if (!first) {
                orderBy.append(", ");
            }
            orderBy.append(column).append(order.isAscending() ? " asc" : " desc");
            first = false;
        }
        if (first) {
            return " order by created_at desc";
        }
        return orderBy.toString();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
