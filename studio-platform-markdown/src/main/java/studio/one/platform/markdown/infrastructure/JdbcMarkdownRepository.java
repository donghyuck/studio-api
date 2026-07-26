package studio.one.platform.markdown.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownDocument;
import studio.one.platform.markdown.domain.MarkdownExtractPart;
import studio.one.platform.markdown.domain.MarkdownLocator;
import studio.one.platform.markdown.domain.MarkdownPipelineExecution;
import studio.one.platform.markdown.domain.MarkdownPipelineExecutionStatus;
import studio.one.platform.markdown.domain.MarkdownPipelineStage;
import studio.one.platform.markdown.domain.MarkdownResource;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

public class JdbcMarkdownRepository implements MarkdownRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcMarkdownRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MarkdownDocument saveDocument(MarkdownDocument document) {
        var params = new MapSqlParameterSource()
                .addValue("documentId", document.documentId())
                .addValue("sourceAttachmentId", document.sourceAttachmentId())
                .addValue("currentRevisionId", document.currentRevisionId())
                .addValue("createdAt", timestamp(document.createdAt()))
                .addValue("updatedAt", timestamp(document.updatedAt()));
        int updated = jdbc.update("""
                UPDATE tb_ai_markdown_document
                SET current_revision_id=:currentRevisionId, updated_at=:updatedAt
                WHERE document_id=:documentId
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_document
                        (document_id, source_attachment_id, current_revision_id, created_at, updated_at)
                    VALUES
                        (:documentId, :sourceAttachmentId, :currentRevisionId, :createdAt, :updatedAt)
                    """, params);
        }
        return findDocument(document.documentId()).orElse(document);
    }

    @Override
    public MarkdownRevision saveRevision(MarkdownRevision revision) {
        var params = revisionParams(revision);
        int updated = jdbc.update("""
                UPDATE tb_ai_markdown_revision
                SET result_attachment_id=:resultAttachmentId,
                    document_convert_job_id=:documentConvertJobId,
                    extractor_version=:extractorVersion,
                    options_json=:optionsJson,
                    options_hash=:optionsHash,
                    content_hash=:contentHash,
                    markdown_text=:markdownText,
                    status=:status,
                    error_code=:errorCode,
                    error_message=:errorMessage,
                    started_at=:startedAt,
                    completed_at=:completedAt,
                    updated_at=:updatedAt
                WHERE revision_id=:revisionId
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_revision (
                        revision_id, document_id, source_attachment_id, result_attachment_id,
                        document_convert_job_id, extractor_type, extractor_version, options_json,
                        options_hash, source_content_hash, content_hash, markdown_text,
                        source_file_name, source_format, source_object_type, source_object_id,
                        status, error_code, error_message, created_at, started_at, completed_at, updated_at
                    ) VALUES (
                        :revisionId, :documentId, :sourceAttachmentId, :resultAttachmentId,
                        :documentConvertJobId, :extractorType, :extractorVersion, :optionsJson,
                        :optionsHash, :sourceContentHash, :contentHash, :markdownText,
                        :sourceFileName, :sourceFormat, :sourceObjectType, :sourceObjectId,
                        :status, :errorCode, :errorMessage, :createdAt, :startedAt, :completedAt, :updatedAt
                    )
                    """, params);
        }
        return findRevision(revision.revisionId()).orElse(revision);
    }

    @Override
    public MarkdownPipelineExecution savePipelineExecution(MarkdownPipelineExecution execution) {
        var params = new MapSqlParameterSource()
                .addValue("revisionId", execution.revisionId())
                .addValue("status", execution.status().name())
                .addValue("currentStage", execution.currentStage().name())
                .addValue("lastCompletedStage", execution.lastCompletedStage() == null
                        ? null : execution.lastCompletedStage().name())
                .addValue("attemptCount", execution.attemptCount())
                .addValue("errorCode", execution.errorCode())
                .addValue("errorMessage", execution.errorMessage())
                .addValue("startedAt", timestamp(execution.startedAt()))
                .addValue("completedAt", timestamp(execution.completedAt()))
                .addValue("updatedAt", timestamp(execution.updatedAt()));
        int updated = jdbc.update("""
                UPDATE tb_ai_markdown_pipeline_execution
                SET status=:status, current_stage=:currentStage,
                    last_completed_stage=:lastCompletedStage, attempt_count=:attemptCount,
                    error_code=:errorCode, error_message=:errorMessage,
                    started_at=:startedAt, completed_at=:completedAt, updated_at=:updatedAt
                WHERE revision_id=:revisionId
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_pipeline_execution (
                        revision_id, status, current_stage, last_completed_stage, attempt_count,
                        error_code, error_message, started_at, completed_at, updated_at
                    ) VALUES (
                        :revisionId, :status, :currentStage, :lastCompletedStage, :attemptCount,
                        :errorCode, :errorMessage, :startedAt, :completedAt, :updatedAt
                    )
                    """, params);
        }
        return findPipelineExecution(execution.revisionId()).orElse(execution);
    }

    @Override
    public Optional<MarkdownDocument> findDocument(String documentId) {
        return jdbc.query("""
                SELECT document_id, source_attachment_id, current_revision_id, created_at, updated_at
                FROM tb_ai_markdown_document WHERE document_id=:documentId
                """, Map.of("documentId", documentId), this::mapDocument).stream().findFirst();
    }

    @Override
    public Optional<MarkdownDocument> findDocumentBySourceAttachmentId(long sourceAttachmentId) {
        return jdbc.query("""
                SELECT document_id, source_attachment_id, current_revision_id, created_at, updated_at
                FROM tb_ai_markdown_document WHERE source_attachment_id=:sourceAttachmentId
                """, Map.of("sourceAttachmentId", sourceAttachmentId), this::mapDocument).stream().findFirst();
    }

    @Override
    public Optional<MarkdownRevision> findRevision(String revisionId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_revision WHERE revision_id=:revisionId
                """, Map.of("revisionId", revisionId), this::mapRevision).stream().findFirst();
    }

    @Override
    public Optional<MarkdownRevision> findRevisionByConvertJobId(String convertJobId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_revision WHERE document_convert_job_id=:convertJobId
                """, Map.of("convertJobId", convertJobId), this::mapRevision).stream().findFirst();
    }

    @Override
    public Optional<MarkdownPipelineExecution> findPipelineExecution(String revisionId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_pipeline_execution WHERE revision_id=:revisionId
                """, Map.of("revisionId", revisionId), (rs, rowNum) -> new MarkdownPipelineExecution(
                rs.getString("revision_id"),
                MarkdownPipelineExecutionStatus.valueOf(rs.getString("status")),
                MarkdownPipelineStage.valueOf(rs.getString("current_stage")),
                enumValue(MarkdownPipelineStage.class, rs.getString("last_completed_stage")),
                rs.getInt("attempt_count"), rs.getString("error_code"), rs.getString("error_message"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("updated_at")))).stream().findFirst();
    }

    @Override
    public int recoverStalePipelineExecutions(Instant staleBefore, Instant now) {
        return jdbc.update("""
                UPDATE tb_ai_markdown_pipeline_execution
                   SET status='FAILED',
                       error_code='PIPELINE_STALE',
                       error_message='Pipeline was marked stale during startup recovery',
                       completed_at=:now,
                       updated_at=:now
                 WHERE status='RUNNING'
                   AND updated_at < :staleBefore
                """, new MapSqlParameterSource()
                .addValue("staleBefore", timestamp(staleBefore))
                .addValue("now", timestamp(now)));
    }

    @Override
    public Optional<MarkdownRevision> findActiveRevisionBySourceAttachmentId(long sourceAttachmentId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_revision
                WHERE source_attachment_id=:sourceAttachmentId
                  AND status IN ('PENDING', 'RUNNING')
                ORDER BY created_at DESC
                LIMIT 1
                """, Map.of("sourceAttachmentId", sourceAttachmentId), this::mapRevision).stream().findFirst();
    }

    @Override
    public Optional<MarkdownRevision> findReusableRevision(long sourceAttachmentId, String sourceContentHash,
            String extractorType, String extractorVersion, String optionsHash) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_revision
                WHERE source_attachment_id=:sourceAttachmentId
                  AND source_content_hash=:sourceContentHash
                  AND extractor_type=:extractorType
                  AND extractor_version=:extractorVersion
                  AND options_hash=:optionsHash
                  AND status='COMPLETED'
                ORDER BY completed_at DESC
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("sourceAttachmentId", sourceAttachmentId)
                .addValue("sourceContentHash", sourceContentHash)
                .addValue("extractorType", extractorType)
                .addValue("extractorVersion", extractorVersion)
                .addValue("optionsHash", optionsHash), this::mapRevision).stream().findFirst();
    }

    @Override
    public List<MarkdownRevision> findRevisions(String documentId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_revision
                WHERE document_id=:documentId
                ORDER BY created_at DESC
                """, Map.of("documentId", documentId), this::mapRevision);
    }

    @Override
    public void replaceLocators(String revisionId, List<MarkdownLocator> locators) {
        jdbc.update("DELETE FROM tb_ai_markdown_locator WHERE revision_id=:revisionId",
                Map.of("revisionId", revisionId));
        for (MarkdownLocator locator : locators) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_locator (
                        locator_id, revision_id, locator_type, locator_no, title,
                        start_offset, end_offset, source_ref, metadata_json
                    ) VALUES (
                        :locatorId, :revisionId, :locatorType, :locatorNo, :title,
                        :startOffset, :endOffset, :sourceRef, :metadataJson
                    )
                    """, new MapSqlParameterSource()
                    .addValue("locatorId", locator.locatorId())
                    .addValue("revisionId", revisionId)
                    .addValue("locatorType", locator.locatorType())
                    .addValue("locatorNo", locator.locatorNo())
                    .addValue("title", locator.title())
                    .addValue("startOffset", locator.startOffset())
                    .addValue("endOffset", locator.endOffset())
                    .addValue("sourceRef", locator.sourceRef())
                    .addValue("metadataJson", locator.metadataJson()));
        }
    }

    @Override
    public void replaceResources(String revisionId, List<MarkdownResource> resources) {
        jdbc.update("DELETE FROM tb_ai_markdown_resource WHERE revision_id=:revisionId",
                Map.of("revisionId", revisionId));
        for (MarkdownResource resource : resources) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_resource (
                        resource_id, revision_id, resource_type, name, attachment_id, metadata_json
                    ) VALUES (
                        :resourceId, :revisionId, :resourceType, :name, :attachmentId, :metadataJson
                    )
                    """, new MapSqlParameterSource()
                    .addValue("resourceId", resource.resourceId())
                    .addValue("revisionId", revisionId)
                    .addValue("resourceType", resource.resourceType())
                    .addValue("name", resource.name())
                    .addValue("attachmentId", resource.attachmentId())
                    .addValue("metadataJson", resource.metadataJson()));
        }
    }

    @Override
    public void replaceExtractParts(String revisionId, List<MarkdownExtractPart> parts) {
        deleteExtractParts(revisionId);
        for (MarkdownExtractPart part : parts) {
            saveExtractPart(part);
        }
    }

    @Override
    public void deleteExtractParts(String revisionId) {
        jdbc.update("DELETE FROM tb_ai_markdown_extract_part WHERE revision_id=:revisionId",
                Map.of("revisionId", revisionId));
    }

    @Override
    public void saveExtractPart(MarkdownExtractPart part) {
        jdbc.update("""
                INSERT INTO tb_ai_markdown_extract_part (
                    part_id, revision_id, page_from, page_to, status, engine,
                    text_length, markdown_text, error_code, error_message, elapsed_ms,
                    metadata_json, created_at, started_at, completed_at
                ) VALUES (
                    :partId, :revisionId, :pageFrom, :pageTo, :status, :engine,
                    :textLength, :markdownText, :errorCode, :errorMessage, :elapsedMs,
                    :metadataJson, :createdAt, :startedAt, :completedAt
                )
                """, extractPartParams(part));
    }

    @Override
    public List<MarkdownLocator> findLocators(String revisionId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_locator
                WHERE revision_id=:revisionId ORDER BY start_offset, locator_no
                """, Map.of("revisionId", revisionId), (rs, rowNum) -> new MarkdownLocator(
                rs.getString("locator_id"), rs.getString("revision_id"), rs.getString("locator_type"),
                integer(rs, "locator_no"), rs.getString("title"), rs.getInt("start_offset"),
                rs.getInt("end_offset"), rs.getString("source_ref"), rs.getString("metadata_json")));
    }

    @Override
    public List<MarkdownResource> findResources(String revisionId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_resource
                WHERE revision_id=:revisionId ORDER BY resource_id
                """, Map.of("revisionId", revisionId), (rs, rowNum) -> mapResource(rs));
    }

    @Override
    public Optional<MarkdownResource> findResource(String revisionId, String resourceType) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_resource
                WHERE revision_id=:revisionId AND resource_type=:resourceType
                ORDER BY resource_id
                LIMIT 1
                """, new MapSqlParameterSource()
                .addValue("revisionId", revisionId)
                .addValue("resourceType", resourceType), (rs, rowNum) -> mapResource(rs))
                .stream().findFirst();
    }

    @Override
    public void upsertResource(MarkdownResource resource) {
        var params = new MapSqlParameterSource()
                .addValue("resourceId", resource.resourceId())
                .addValue("revisionId", resource.revisionId())
                .addValue("resourceType", resource.resourceType())
                .addValue("name", resource.name())
                .addValue("attachmentId", resource.attachmentId())
                .addValue("metadataJson", resource.metadataJson());
        int updated = jdbc.update("""
                UPDATE tb_ai_markdown_resource
                SET resource_type=:resourceType, name=:name,
                    attachment_id=:attachmentId, metadata_json=:metadataJson
                WHERE resource_id=:resourceId
                """, params);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tb_ai_markdown_resource (
                        resource_id, revision_id, resource_type, name, attachment_id, metadata_json
                    ) VALUES (
                        :resourceId, :revisionId, :resourceType, :name, :attachmentId, :metadataJson
                    )
                    """, params);
        }
    }

    @Override
    public List<MarkdownExtractPart> findExtractParts(String revisionId) {
        return jdbc.query("""
                SELECT * FROM tb_ai_markdown_extract_part
                WHERE revision_id=:revisionId ORDER BY page_from, page_to
                """, Map.of("revisionId", revisionId), this::mapExtractPart);
    }

    private MarkdownDocument mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new MarkdownDocument(rs.getString("document_id"), rs.getLong("source_attachment_id"),
                rs.getString("current_revision_id"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private MarkdownResource mapResource(ResultSet rs) throws SQLException {
        return new MarkdownResource(
                rs.getString("resource_id"),
                rs.getString("revision_id"),
                rs.getString("resource_type"),
                rs.getString("name"),
                longValue(rs, "attachment_id"),
                rs.getString("metadata_json"));
    }

    private MarkdownRevision mapRevision(ResultSet rs, int rowNum) throws SQLException {
        return new MarkdownRevision(
                rs.getString("revision_id"), rs.getString("document_id"), rs.getLong("source_attachment_id"),
                longValue(rs, "result_attachment_id"), rs.getString("document_convert_job_id"),
                rs.getString("extractor_type"), rs.getString("extractor_version"), rs.getString("options_json"),
                rs.getString("options_hash"), rs.getString("source_content_hash"), rs.getString("content_hash"),
                rs.getString("markdown_text"), rs.getString("source_file_name"), rs.getString("source_format"),
                rs.getString("source_object_type"), rs.getString("source_object_id"),
                MarkdownRevisionStatus.valueOf(rs.getString("status")), rs.getString("error_code"),
                rs.getString("error_message"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private MarkdownExtractPart mapExtractPart(ResultSet rs, int rowNum) throws SQLException {
        return new MarkdownExtractPart(
                rs.getString("part_id"),
                rs.getString("revision_id"),
                rs.getInt("page_from"),
                rs.getInt("page_to"),
                rs.getString("status"),
                rs.getString("engine"),
                rs.getInt("text_length"),
                rs.getString("markdown_text"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                longValue(rs, "elapsed_ms"),
                rs.getString("metadata_json"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")));
    }

    private MapSqlParameterSource revisionParams(MarkdownRevision revision) {
        return new MapSqlParameterSource()
                .addValue("revisionId", revision.revisionId())
                .addValue("documentId", revision.documentId())
                .addValue("sourceAttachmentId", revision.sourceAttachmentId())
                .addValue("resultAttachmentId", revision.resultAttachmentId())
                .addValue("documentConvertJobId", revision.documentConvertJobId())
                .addValue("extractorType", revision.extractorType())
                .addValue("extractorVersion", revision.extractorVersion())
                .addValue("optionsJson", revision.optionsJson())
                .addValue("optionsHash", revision.optionsHash())
                .addValue("sourceContentHash", revision.sourceContentHash())
                .addValue("contentHash", revision.contentHash())
                .addValue("markdownText", revision.markdownText())
                .addValue("sourceFileName", revision.sourceFileName())
                .addValue("sourceFormat", revision.sourceFormat())
                .addValue("sourceObjectType", revision.sourceObjectType())
                .addValue("sourceObjectId", revision.sourceObjectId())
                .addValue("status", revision.status().name())
                .addValue("errorCode", revision.errorCode())
                .addValue("errorMessage", revision.errorMessage())
                .addValue("createdAt", timestamp(revision.createdAt()))
                .addValue("startedAt", timestamp(revision.startedAt()))
                .addValue("completedAt", timestamp(revision.completedAt()))
                .addValue("updatedAt", timestamp(revision.updatedAt()));
    }

    private MapSqlParameterSource extractPartParams(MarkdownExtractPart part) {
        return new MapSqlParameterSource()
                .addValue("partId", part.partId())
                .addValue("revisionId", part.revisionId())
                .addValue("pageFrom", part.pageFrom())
                .addValue("pageTo", part.pageTo())
                .addValue("status", part.status())
                .addValue("engine", part.engine())
                .addValue("textLength", part.textLength())
                .addValue("markdownText", part.markdownText())
                .addValue("errorCode", part.errorCode())
                .addValue("errorMessage", part.errorMessage())
                .addValue("elapsedMs", part.elapsedMs())
                .addValue("metadataJson", part.metadataJson())
                .addValue("createdAt", timestamp(part.createdAt()))
                .addValue("startedAt", timestamp(part.startedAt()))
                .addValue("completedAt", timestamp(part.completedAt()));
    }

    private static Long longValue(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
