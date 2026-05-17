package studio.one.platform.skillgraph.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;

@RequiredArgsConstructor
public class JdbcSkillRagExtractionJobStore implements SkillRagExtractionJobStore {

    private final NamedParameterJdbcTemplate template;

    @Override
    public SkillRagExtractionJob saveJob(SkillRagExtractionJob job) {
        int updated = template.update("""
                UPDATE tb_skill_rag_extraction_job
                SET status = :status,
                    requested_chunks = :requestedChunks,
                    total_chunks = :totalChunks,
                    processed_chunks = :processedChunks,
                    succeeded_chunks = :succeededChunks,
                    failed_chunks = :failedChunks,
                    extracted_count = :extractedCount,
                    error_message = :error,
                    updated_at = :updatedAt
                WHERE job_id = :jobId
                """, jobParams(job));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_rag_extraction_job
                        (job_id, object_type, object_id, document_id, status, requested_chunks, total_chunks,
                         processed_chunks, succeeded_chunks, failed_chunks, extracted_count, error_message,
                         created_at, updated_at)
                    VALUES
                        (:jobId, :objectType, :objectId, :documentId, :status, :requestedChunks, :totalChunks,
                         :processedChunks, :succeededChunks, :failedChunks, :extractedCount, :error,
                         :createdAt, :updatedAt)
                    """, jobParams(job));
        }
        return job;
    }

    @Override
    public Optional<SkillRagExtractionJob> findJob(String jobId) {
        List<SkillRagExtractionJob> rows = template.query("""
                SELECT * FROM tb_skill_rag_extraction_job WHERE job_id = :jobId
                """, new MapSqlParameterSource("jobId", jobId), this::mapJob);
        return rows.stream().findFirst();
    }

    @Override
    public SkillRagExtractionJobItem saveItem(SkillRagExtractionJobItem item) {
        int updated = template.update("""
                UPDATE tb_skill_rag_extraction_job_item
                SET document_id = :documentId,
                    source_id = :sourceId,
                    source_chunk_id = :sourceChunkId,
                    extracted_count = :extractedCount,
                    status = :status,
                    error_message = :error,
                    updated_at = :updatedAt
                WHERE job_id = :jobId AND chunk_id = :chunkId
                """, itemParams(item));
        if (updated == 0) {
            template.update("""
                    INSERT INTO tb_skill_rag_extraction_job_item
                        (job_id, chunk_id, document_id, source_id, source_chunk_id, extracted_count, status,
                         error_message, created_at, updated_at)
                    VALUES
                        (:jobId, :chunkId, :documentId, :sourceId, :sourceChunkId, :extractedCount, :status,
                         :error, :createdAt, :updatedAt)
                    """, itemParams(item));
        }
        return item;
    }

    @Override
    public List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit) {
        return template.query("""
                SELECT * FROM tb_skill_rag_extraction_job_item
                WHERE job_id = :jobId
                ORDER BY created_at, chunk_id
                LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("offset", Math.max(0, offset))
                .addValue("limit", limit <= 0 ? 100 : limit), this::mapItem);
    }

    @Override
    public List<SkillRagExtractionJobItem> listItemsByStatus(
            String jobId,
            SkillRagExtractionItemStatus status,
            int limit) {
        return template.query("""
                SELECT * FROM tb_skill_rag_extraction_job_item
                WHERE job_id = :jobId AND status = :status
                ORDER BY created_at, chunk_id
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("status", status.name())
                .addValue("limit", limit <= 0 ? 100 : limit), this::mapItem);
    }

    private MapSqlParameterSource jobParams(SkillRagExtractionJob job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId())
                .addValue("objectType", job.objectType())
                .addValue("objectId", job.objectId())
                .addValue("documentId", job.documentId())
                .addValue("status", job.status().name())
                .addValue("requestedChunks", job.requestedChunks())
                .addValue("totalChunks", job.totalChunks())
                .addValue("processedChunks", job.processedChunks())
                .addValue("succeededChunks", job.succeededChunks())
                .addValue("failedChunks", job.failedChunks())
                .addValue("extractedCount", job.extractedCount())
                .addValue("error", job.error())
                .addValue("createdAt", Timestamp.from(job.createdAt()))
                .addValue("updatedAt", Timestamp.from(job.updatedAt()));
    }

    private MapSqlParameterSource itemParams(SkillRagExtractionJobItem item) {
        return new MapSqlParameterSource()
                .addValue("jobId", item.jobId())
                .addValue("chunkId", item.chunkId())
                .addValue("documentId", item.documentId())
                .addValue("sourceId", item.sourceId())
                .addValue("sourceChunkId", item.sourceChunkId())
                .addValue("extractedCount", item.extractedCount())
                .addValue("status", item.status().name())
                .addValue("error", item.error())
                .addValue("createdAt", Timestamp.from(item.createdAt()))
                .addValue("updatedAt", Timestamp.from(item.updatedAt()));
    }

    private SkillRagExtractionJob mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new SkillRagExtractionJob(
                rs.getString("job_id"),
                rs.getString("object_type"),
                rs.getString("object_id"),
                rs.getString("document_id"),
                SkillRagExtractionJobStatus.valueOf(rs.getString("status")),
                rs.getInt("requested_chunks"),
                rs.getInt("total_chunks"),
                rs.getInt("processed_chunks"),
                rs.getInt("succeeded_chunks"),
                rs.getInt("failed_chunks"),
                rs.getInt("extracted_count"),
                rs.getString("error_message"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private SkillRagExtractionJobItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new SkillRagExtractionJobItem(
                rs.getString("job_id"),
                rs.getString("chunk_id"),
                rs.getString("document_id"),
                rs.getString("source_id"),
                rs.getString("source_chunk_id"),
                rs.getInt("extracted_count"),
                SkillRagExtractionItemStatus.valueOf(rs.getString("status")),
                rs.getString("error_message"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
