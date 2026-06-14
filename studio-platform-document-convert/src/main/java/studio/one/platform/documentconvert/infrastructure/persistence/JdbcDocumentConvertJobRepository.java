package studio.one.platform.documentconvert.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobRepository;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;
import studio.one.platform.documentconvert.domain.type.DocumentConvertStatus;
import studio.one.platform.documentconvert.domain.type.DocumentFormat;

public class JdbcDocumentConvertJobRepository implements DocumentConvertJobRepository {
    private static final String COLUMNS = """
            id, job_id, source_file_id, source_format, target_format, status, options_json,
            result_file_id, error_code, error_message, retry_count, requested_by,
            created_at, started_at, completed_at, updated_at
            """;
    private static final RowMapper<DocumentConvertJob> MAPPER = (rs, rowNum) -> new DocumentConvertJob(
            rs.getLong("id"), rs.getString("job_id"), rs.getString("source_file_id"),
            DocumentFormat.valueOf(rs.getString("source_format")),
            DocumentFormat.valueOf(rs.getString("target_format")),
            DocumentConvertStatus.valueOf(rs.getString("status")), rs.getString("options_json"),
            rs.getString("result_file_id"), rs.getString("error_code"), rs.getString("error_message"),
            rs.getInt("retry_count"), rs.getString("requested_by"), instant(rs.getTimestamp("created_at")),
            instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
            instant(rs.getTimestamp("updated_at")));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcDocumentConvertJobRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DocumentConvertJob save(DocumentConvertJob job) {
        MapSqlParameterSource params = params(job);
        int updated = jdbc.update("""
                update tb_document_convert_job set status=:status, result_file_id=:resultFileId,
                    error_code=:errorCode, error_message=:errorMessage, retry_count=:retryCount,
                    started_at=:startedAt, completed_at=:completedAt, updated_at=:updatedAt
                where job_id=:jobId
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    insert into tb_document_convert_job (
                        job_id, source_file_id, source_format, target_format, status, options_json,
                        result_file_id, error_code, error_message, retry_count, requested_by,
                        created_at, started_at, completed_at, updated_at
                    ) values (
                        :jobId, :sourceFileId, :sourceFormat, :targetFormat, :status, :optionsJson,
                        :resultFileId, :errorCode, :errorMessage, :retryCount, :requestedBy,
                        :createdAt, :startedAt, :completedAt, :updatedAt
                    )
                    """, params);
        }
        return findByJobId(job.jobId()).orElse(job);
    }

    @Override
    public Optional<DocumentConvertJob> findByJobId(String jobId) {
        return jdbc.query("select " + COLUMNS + " from tb_document_convert_job where job_id=:jobId",
                Map.of("jobId", jobId), MAPPER).stream().findFirst();
    }

    private MapSqlParameterSource params(DocumentConvertJob job) {
        return new MapSqlParameterSource()
                .addValue("jobId", job.jobId()).addValue("sourceFileId", job.sourceFileId())
                .addValue("sourceFormat", job.sourceFormat().name()).addValue("targetFormat", job.targetFormat().name())
                .addValue("status", job.status().name()).addValue("optionsJson", job.optionsJson())
                .addValue("resultFileId", job.resultFileId()).addValue("errorCode", job.errorCode())
                .addValue("errorMessage", job.errorMessage()).addValue("retryCount", job.retryCount())
                .addValue("requestedBy", job.requestedBy()).addValue("createdAt", timestamp(job.createdAt()))
                .addValue("startedAt", timestamp(job.startedAt())).addValue("completedAt", timestamp(job.completedAt()))
                .addValue("updatedAt", timestamp(job.updatedAt()));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
