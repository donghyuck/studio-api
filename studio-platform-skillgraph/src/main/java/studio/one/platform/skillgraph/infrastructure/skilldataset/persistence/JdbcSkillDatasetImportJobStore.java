package studio.one.platform.skillgraph.infrastructure.skilldataset.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJob;
import studio.one.platform.skillgraph.domain.model.SkillDatasetImportJobStatus;
import studio.one.platform.skillgraph.domain.port.SkillDatasetImportJobStore;

@RequiredArgsConstructor
public class JdbcSkillDatasetImportJobStore implements SkillDatasetImportJobStore {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public SkillDatasetImportJob save(SkillDatasetImportJob job) {
        jdbcTemplate.update("""
            insert into tb_skill_dataset_import_job (
                job_id, provider, dataset_id, dataset_name, version, language,
                source_location, status, total_rows, processed_rows,
                created_concepts, created_relations, failed_rows,
                error_message, created_at, started_at, completed_at, updated_at
            ) values (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, current_timestamp
            )
            on conflict (job_id) do update set
                provider = excluded.provider,
                dataset_id = excluded.dataset_id,
                dataset_name = excluded.dataset_name,
                version = excluded.version,
                language = excluded.language,
                source_location = excluded.source_location,
                status = excluded.status,
                total_rows = excluded.total_rows,
                processed_rows = excluded.processed_rows,
                created_concepts = excluded.created_concepts,
                created_relations = excluded.created_relations,
                failed_rows = excluded.failed_rows,
                error_message = excluded.error_message,
                started_at = excluded.started_at,
                completed_at = excluded.completed_at,
                updated_at = current_timestamp
            """,
                job.jobId(),
                job.provider(),
                job.datasetId(),
                job.datasetName(),
                job.version(),
                job.language(),
                job.sourceLocation(),
                job.status().name(),
                job.totalRows(),
                job.processedRows(),
                job.createdConcepts(),
                job.createdRelations(),
                job.failedRows(),
                job.errorMessage(),
                timestamp(job.createdAt()),
                timestamp(job.startedAt()),
                timestamp(job.completedAt())
        );

        return findById(job.jobId()).orElse(job);
    }

    @Override
    public Optional<SkillDatasetImportJob> findById(String jobId) {
        return jdbcTemplate.query("""
            select job_id, provider, dataset_id, dataset_name, version, language,
                   source_location, status, total_rows, processed_rows,
                   created_concepts, created_relations, failed_rows,
                   error_message, created_at, started_at, completed_at
              from tb_skill_dataset_import_job
             where job_id = ?
            """, mapper(), jobId).stream().findFirst();
    }

    @Override
    public List<SkillDatasetImportJob> findRecent(int limit) {
        return jdbcTemplate.query("""
            select job_id, provider, dataset_id, dataset_name, version, language,
                   source_location, status, total_rows, processed_rows,
                   created_concepts, created_relations, failed_rows,
                   error_message, created_at, started_at, completed_at
              from tb_skill_dataset_import_job
             order by created_at desc
             limit ?
            """, mapper(), normalizeLimit(limit));
    }

    private int normalizeLimit(int limit) {
        return limit <= 0 ? 20 : Math.min(limit, 100);
    }

    private RowMapper<SkillDatasetImportJob> mapper() {
        return (rs, rowNum) -> new SkillDatasetImportJob(
                rs.getString("job_id"),
                rs.getString("provider"),
                rs.getString("dataset_id"),
                rs.getString("dataset_name"),
                rs.getString("version"),
                rs.getString("language"),
                rs.getString("source_location"),
                SkillDatasetImportJobStatus.valueOf(rs.getString("status")),
                rs.getLong("total_rows"),
                rs.getLong("processed_rows"),
                rs.getLong("created_concepts"),
                rs.getLong("created_relations"),
                rs.getLong("failed_rows"),
                rs.getString("error_message"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at")
        );
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
