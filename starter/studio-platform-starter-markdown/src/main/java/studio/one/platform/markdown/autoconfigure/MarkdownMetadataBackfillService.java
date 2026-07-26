package studio.one.platform.markdown.autoconfigure;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.documentmetadata.DocumentMetadataProjectionPolicy;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.markdown.application.MarkdownPipelineOptions;
import studio.one.platform.markdown.application.port.MarkdownMetadataEnrichmentPort;
import studio.one.platform.markdown.application.port.MarkdownRepository;
import studio.one.platform.markdown.domain.MarkdownRevision;
import studio.one.platform.markdown.domain.MarkdownRevisionStatus;

/**
 * Dedicated metadata-only backfill coordinator. It never creates a content
 * revision or requests re-chunking/re-embedding.
 */
public final class MarkdownMetadataBackfillService {

    private static final String JOB_TABLE = "tb_ai_markdown_metadata_backfill_job";
    private static final String ITEM_TABLE = "tb_ai_markdown_metadata_backfill_item";
    private static final String RESOURCE_TABLE = "tb_ai_markdown_resource";
    private static final String NORMALIZED_TYPE = "NORMALIZED_DOCUMENT";
    private static final String WORKER_ID = UUID.randomUUID().toString();
    private static final int MAX_LIMIT = 10_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final MarkdownRepository repository;
    private final MarkdownMetadataEnrichmentPort enrichment;
    private final ObjectMapper objectMapper;
    private final TaskExecutor executor;
    private final ObjectProvider<RagPipelineService> ragPipelineProvider;
    private final DocumentMetadataProjectionPolicy projectionPolicy = new DocumentMetadataProjectionPolicy();

    public MarkdownMetadataBackfillService(
            NamedParameterJdbcTemplate jdbc,
            MarkdownRepository repository,
            MarkdownMetadataEnrichmentPort enrichment,
            ObjectMapper objectMapper,
            TaskExecutor executor,
            ObjectProvider<RagPipelineService> ragPipelineProvider) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.enrichment = enrichment;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.ragPipelineProvider = ragPipelineProvider;
    }

    public JobView create(CreateRequest request, String principal) {
        CreateRequest normalized = request == null ? CreateRequest.defaults() : request.normalized();
        ensureNoActiveJob(principal);
        String settingsJson = write(normalized.settings());
        String fingerprint = fingerprint(settingsJson);
        if (normalized.mode() == Mode.APPLY) {
            validateDryRun(normalized.dryRunJobId(), normalized.settingsFingerprint(), fingerprint);
        }
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO %s (
                    job_id, mode, status, requested_by, settings_json, settings_fingerprint,
                    dry_run_job_id, cancel_requested, total_count, completed_count, failed_count,
                    created_at, updated_at)
                VALUES (
                    :jobId, :mode, 'PENDING', :requestedBy, :settingsJson, :fingerprint,
                    :dryRunJobId, false, 0, 0, 0, :now, :now)
                """.formatted(JOB_TABLE), Map.of(
                "jobId", jobId,
                "mode", normalized.mode().name(),
                "requestedBy", safePrincipal(principal),
                "settingsJson", settingsJson,
                "fingerprint", fingerprint,
                "dryRunJobId", normalized.dryRunJobId() == null ? "" : normalized.dryRunJobId(),
                "now", Timestamp.from(now)));
        createItems(jobId, normalized.limit(), now);
        refreshCounts(jobId);
        executor.execute(() -> process(jobId, normalized));
        return get(jobId);
    }

    public List<JobView> list(int limit) {
        return jdbc.query("""
                SELECT * FROM %s ORDER BY created_at DESC LIMIT :limit
                """.formatted(JOB_TABLE), Map.of("limit", boundedLimit(limit)), (rs, rowNum) -> jobView(rs));
    }

    public JobView get(String jobId) {
        return jdbc.query("""
                SELECT * FROM %s WHERE job_id = :jobId
                """.formatted(JOB_TABLE), Map.of("jobId", jobId), (rs, rowNum) -> jobView(rs))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Metadata backfill job not found: " + jobId));
    }

    public List<ItemView> items(String jobId, int limit) {
        get(jobId);
        return jdbc.query("""
                SELECT * FROM %s WHERE job_id = :jobId ORDER BY item_id LIMIT :limit
                """.formatted(ITEM_TABLE), Map.of("jobId", jobId, "limit", boundedLimit(limit)),
                (rs, rowNum) -> new ItemView(
                        rs.getString("item_id"),
                        rs.getString("job_id"),
                        rs.getString("document_id"),
                        rs.getString("revision_id"),
                        rs.getString("content_hash"),
                        rs.getString("status"),
                        rs.getString("artifact_fingerprint"),
                        rs.getString("error_code")));
    }

    public JobView retry(String jobId) {
        JobView job = get(jobId);
        if ("RUNNING".equals(job.status()) || "PENDING".equals(job.status())) {
            throw new IllegalStateException("Active metadata backfill job cannot be retried");
        }
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE %s SET status = 'PENDING', error_code = NULL, updated_at = :now
                 WHERE job_id = :jobId AND status IN ('FAILED', 'BLOCKED_MODEL_CONFIGURATION')
                """.formatted(ITEM_TABLE), Map.of("jobId", jobId, "now", Timestamp.from(now)));
        jdbc.update("""
                UPDATE %s
                   SET status = 'PENDING', cancel_requested = false, lease_owner = NULL,
                       lease_expires_at = NULL, completed_at = NULL, updated_at = :now
                 WHERE job_id = :jobId
                """.formatted(JOB_TABLE), Map.of("jobId", jobId, "now", Timestamp.from(now)));
        CreateRequest settings = readSettings(job.settingsJson(), job.mode(), job.dryRunJobId(), job.settingsFingerprint());
        executor.execute(() -> process(jobId, settings));
        return get(jobId);
    }

    public JobView cancel(String jobId) {
        get(jobId);
        jdbc.update("""
                UPDATE %s SET cancel_requested = true, updated_at = :now WHERE job_id = :jobId
                """.formatted(JOB_TABLE), Map.of("jobId", jobId, "now", Timestamp.from(Instant.now())));
        return get(jobId);
    }

    private void createItems(String jobId, int limit, Instant now) {
        List<Map<String, Object>> revisions = jdbc.queryForList("""
                SELECT d.document_id, r.revision_id, r.content_hash
                  FROM tb_ai_markdown_document d
                  JOIN tb_ai_markdown_revision r ON r.revision_id = d.current_revision_id
                 WHERE r.status = 'COMPLETED'
                 ORDER BY d.document_id
                 LIMIT :limit
                """, Map.of("limit", boundedLimit(limit)));
        for (Map<String, Object> revision : revisions) {
            String revisionId = revision.get("revision_id").toString();
            boolean normalized = repository.findResource(revisionId, NORMALIZED_TYPE).isPresent();
            jdbc.update("""
                    INSERT INTO %s (
                        item_id, job_id, document_id, revision_id, content_hash,
                        status, created_at, updated_at)
                    VALUES (
                        :itemId, :jobId, :documentId, :revisionId, :contentHash,
                        :status, :now, :now)
                    """.formatted(ITEM_TABLE), Map.of(
                    "itemId", UUID.nameUUIDFromBytes((jobId + "|" + revisionId)
                            .getBytes(StandardCharsets.UTF_8)).toString(),
                    "jobId", jobId,
                    "documentId", revision.get("document_id").toString(),
                    "revisionId", revisionId,
                    "contentHash", Optional.ofNullable(revision.get("content_hash")).orElse("").toString(),
                    "status", normalized ? "PENDING" : "PARTIAL_NO_NORMALIZED_SOURCE",
                    "now", Timestamp.from(now)));
        }
    }

    private void process(String jobId, CreateRequest request) {
        if (!acquireLease(jobId)) {
            return;
        }
        try {
            for (ItemView item : items(jobId, MAX_LIMIT)) {
                if (!"PENDING".equals(item.status()) || cancelRequested(jobId)) {
                    continue;
                }
                heartbeat(jobId);
                processItem(item, request);
            }
            refreshCounts(jobId);
            finish(jobId, cancelRequested(jobId) ? "CANCELED" : failedCount(jobId) > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        } catch (RuntimeException ex) {
            finish(jobId, "FAILED");
        }
    }

    private void processItem(ItemView item, CreateRequest request) {
        Optional<MarkdownRevision> current = repository.findDocument(item.documentId())
                .flatMap(document -> document.currentRevisionId() == null
                        ? Optional.empty()
                        : repository.findRevision(document.currentRevisionId()));
        if (current.isEmpty()
                || !current.get().revisionId().equals(item.revisionId())
                || !current.get().status().equals(MarkdownRevisionStatus.COMPLETED)
                || !same(item.contentHash(), current.get().contentHash())) {
            updateItem(item.itemId(), "SKIPPED_REVISION_CHANGED", null, null);
            return;
        }
        try {
            MarkdownPipelineOptions options = options(current.get(), request.settings());
            DocumentMetadataArtifact before = existing(item.revisionId()).orElse(null);
            DocumentMetadataArtifact generated = enrichment.preview(current.get(), options);
            boolean artifactChanged = before == null || !before.fingerprint().equals(generated.fingerprint());
            VectorPatchPlan vectorPatch = vectorPatchPlan(current.get(), generated);
            if (request.mode() == Mode.DRY_RUN) {
                updateItem(
                        item.itemId(),
                        artifactChanged || vectorPatch.requiresPatch() ? "WOULD_UPDATE" : "UNCHANGED",
                        generated.fingerprint(),
                        vectorPatch.errorCode());
                return;
            }
            if (artifactChanged) {
                enrichment.enrich(current.get(), options);
            }
            if (vectorPatch.blocked()) {
                updateItem(item.itemId(), "BLOCKED_MODEL_CONFIGURATION", generated.fingerprint(),
                        vectorPatch.errorCode());
                return;
            }
            if (vectorPatch.requiresPatch()) {
                patchVectors(vectorPatch, generated);
            }
            if (!artifactChanged && !vectorPatch.requiresPatch()) {
                updateItem(item.itemId(), "UNCHANGED", generated.fingerprint(), null);
                return;
            }
            updateItem(item.itemId(), "UPDATED_METADATA_ONLY", generated.fingerprint(), null);
        } catch (VectorMetadataPatchUnsupportedException ex) {
            updateItem(item.itemId(), "BLOCKED_MODEL_CONFIGURATION", null, "VECTOR_METADATA_PATCH_UNSUPPORTED");
        } catch (IllegalStateException ex) {
            String code = ex.getMessage() != null && ex.getMessage().contains("deployment")
                    ? "BLOCKED_MODEL_CONFIGURATION" : "FAILED";
            updateItem(item.itemId(), code, null, safeCode(ex));
        } catch (RuntimeException ex) {
            updateItem(item.itemId(), "FAILED", null, safeCode(ex));
        }
    }

    private VectorPatchPlan vectorPatchPlan(MarkdownRevision revision, DocumentMetadataArtifact artifact) {
        RagPipelineService pipeline = ragPipelineProvider == null ? null : ragPipelineProvider.getIfAvailable();
        if (pipeline == null) {
            return VectorPatchPlan.none();
        }
        String objectId = String.valueOf(revision.sourceAttachmentId());
        List<RagSearchResult> rows = pipeline.listByObject("attachment", objectId, 1);
        if (rows.isEmpty()) {
            return VectorPatchPlan.none();
        }
        Map<String, Object> existingMetadata = rows.get(0).metadata();
        if (!hasCanonicalEmbeddingIdentity(existingMetadata)) {
            return VectorPatchPlan.blocked(
                    pipeline,
                    objectId,
                    "VECTOR_EMBEDDING_IDENTITY_INCOMPLETE");
        }
        Map<String, Object> compact = projectionPolicy.compact(artifact);
        boolean requiresPatch = compact.entrySet().stream()
                .anyMatch(entry -> !java.util.Objects.equals(existingMetadata.get(entry.getKey()), entry.getValue()));
        return requiresPatch ? VectorPatchPlan.patch(pipeline, objectId) : VectorPatchPlan.none();
    }

    private void patchVectors(VectorPatchPlan plan, DocumentMetadataArtifact artifact) {
        try {
            plan.pipeline().patchMetadataByObject(
                    "attachment",
                    plan.objectId(),
                    projectionPolicy.compact(artifact));
        } catch (UnsupportedOperationException ex) {
            throw new VectorMetadataPatchUnsupportedException();
        }
    }

    private static boolean hasCanonicalEmbeddingIdentity(Map<String, Object> metadata) {
        return usable(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL_ID))
                && (usable(metadata.get(VectorRecord.KEY_EMBEDDING_SPACE_ID_V2))
                        || usable(metadata.get(VectorRecord.KEY_EMBEDDING_SPACE_ID)));
    }

    private static boolean usable(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().trim();
        return !text.isEmpty() && !"unknown".equalsIgnoreCase(text);
    }

    private record VectorPatchPlan(
            RagPipelineService pipeline,
            String objectId,
            boolean requiresPatch,
            boolean blocked,
            String errorCode) {

        private static VectorPatchPlan none() {
            return new VectorPatchPlan(null, null, false, false, null);
        }

        private static VectorPatchPlan patch(RagPipelineService pipeline, String objectId) {
            return new VectorPatchPlan(pipeline, objectId, true, false, null);
        }

        private static VectorPatchPlan blocked(
                RagPipelineService pipeline,
                String objectId,
                String errorCode) {
            return new VectorPatchPlan(pipeline, objectId, false, true, errorCode);
        }
    }

    private static final class VectorMetadataPatchUnsupportedException extends IllegalStateException {
        private VectorMetadataPatchUnsupportedException() {
            super("Vector metadata patch is not supported by the configured vector store");
        }
    }

    private MarkdownPipelineOptions options(MarkdownRevision revision, Settings settings) {
        try {
            MarkdownPipelineOptions stored = objectMapper.readValue(revision.optionsJson(), MarkdownPipelineOptions.class);
            return stored.withMetadataOptions(
                    settings.documentSemanticType(),
                    settings.metadataEnrichmentMode());
        } catch (Exception ex) {
            return MarkdownPipelineOptions.none().withMetadataOptions(
                    settings.documentSemanticType(),
                    settings.metadataEnrichmentMode());
        }
    }

    private Optional<DocumentMetadataArtifact> existing(String revisionId) {
        return repository.findResource(revisionId, MarkdownDocumentMetadataService.RESOURCE_TYPE)
                .flatMap(resource -> {
                    try {
                        return Optional.of(objectMapper.readValue(
                                resource.metadataJson(), DocumentMetadataArtifact.class));
                    } catch (Exception ignored) {
                        return Optional.empty();
                    }
                });
    }

    private boolean acquireLease(String jobId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE %s
                   SET status = 'RUNNING', lease_owner = :owner, lease_expires_at = :expires,
                       heartbeat_at = :now, started_at = COALESCE(started_at, :now), updated_at = :now
                 WHERE job_id = :jobId
                   AND status = 'PENDING'
                   AND (lease_expires_at IS NULL OR lease_expires_at < :now)
                """.formatted(JOB_TABLE), Map.of(
                "owner", WORKER_ID,
                "expires", Timestamp.from(now.plus(2, ChronoUnit.MINUTES)),
                "now", Timestamp.from(now),
                "jobId", jobId)) == 1;
    }

    private void heartbeat(String jobId) {
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE %s SET heartbeat_at = :now, lease_expires_at = :expires, updated_at = :now
                 WHERE job_id = :jobId AND lease_owner = :owner
                """.formatted(JOB_TABLE), Map.of(
                "now", Timestamp.from(now),
                "expires", Timestamp.from(now.plus(2, ChronoUnit.MINUTES)),
                "jobId", jobId,
                "owner", WORKER_ID));
    }

    private void updateItem(String itemId, String status, String artifactFingerprint, String errorCode) {
        jdbc.update("""
                UPDATE %s
                   SET status = :status, artifact_fingerprint = :fingerprint,
                       error_code = :errorCode, updated_at = :now
                 WHERE item_id = :itemId
                """.formatted(ITEM_TABLE), nullableMap(
                "status", status,
                "fingerprint", artifactFingerprint,
                "errorCode", errorCode,
                "now", Timestamp.from(Instant.now()),
                "itemId", itemId));
    }

    private void refreshCounts(String jobId) {
        jdbc.update("""
                UPDATE %s
                   SET total_count = (SELECT COUNT(*) FROM %s WHERE job_id = :jobId),
                       completed_count = (SELECT COUNT(*) FROM %s
                           WHERE job_id = :jobId AND status NOT IN ('PENDING','RUNNING','FAILED')),
                       failed_count = (SELECT COUNT(*) FROM %s
                           WHERE job_id = :jobId AND status IN ('FAILED','BLOCKED_MODEL_CONFIGURATION')),
                       updated_at = :now
                 WHERE job_id = :jobId
                """.formatted(JOB_TABLE, ITEM_TABLE, ITEM_TABLE, ITEM_TABLE),
                Map.of("jobId", jobId, "now", Timestamp.from(Instant.now())));
    }

    private void finish(String jobId, String status) {
        refreshCounts(jobId);
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE %s
                   SET status = :status, lease_owner = NULL, lease_expires_at = NULL,
                       completed_at = :now, updated_at = :now
                 WHERE job_id = :jobId
                """.formatted(JOB_TABLE), Map.of(
                "status", status, "now", Timestamp.from(now), "jobId", jobId));
    }

    private long failedCount(String jobId) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s
                 WHERE job_id = :jobId AND status IN ('FAILED','BLOCKED_MODEL_CONFIGURATION')
                """.formatted(ITEM_TABLE), Map.of("jobId", jobId), Long.class);
        return value == null ? 0L : value;
    }

    private boolean cancelRequested(String jobId) {
        Boolean value = jdbc.queryForObject("""
                SELECT cancel_requested FROM %s WHERE job_id = :jobId
                """.formatted(JOB_TABLE), Map.of("jobId", jobId), Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    private void ensureNoActiveJob(String principal) {
        Integer global = jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s WHERE status IN ('PENDING','RUNNING')
                """.formatted(JOB_TABLE), Map.of(), Integer.class);
        Integer scoped = jdbc.queryForObject("""
                SELECT COUNT(*) FROM %s
                 WHERE status IN ('PENDING','RUNNING') AND requested_by = :principal
                """.formatted(JOB_TABLE), Map.of("principal", safePrincipal(principal)), Integer.class);
        if ((global != null && global > 0) || (scoped != null && scoped > 0)) {
            throw new IllegalStateException("A metadata backfill job is already active");
        }
    }

    private void validateDryRun(String dryRunJobId, String suppliedFingerprint, String actualFingerprint) {
        if (dryRunJobId == null || dryRunJobId.isBlank()) {
            throw new IllegalArgumentException("APPLY requires dryRunJobId");
        }
        JobView dryRun = get(dryRunJobId);
        if (!Mode.DRY_RUN.name().equals(dryRun.mode()) || !"COMPLETED".equals(dryRun.status())) {
            throw new IllegalStateException("APPLY requires a completed DRY_RUN job");
        }
        if (!dryRun.settingsFingerprint().equals(actualFingerprint)
                || suppliedFingerprint == null
                || !suppliedFingerprint.equals(dryRun.settingsFingerprint())) {
            throw new IllegalArgumentException("DRY_RUN settings fingerprint does not match");
        }
    }

    private JobView jobView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new JobView(
                rs.getString("job_id"),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getString("requested_by"),
                rs.getString("settings_json"),
                rs.getString("settings_fingerprint"),
                blankToNull(rs.getString("dry_run_job_id")),
                rs.getBoolean("cancel_requested"),
                rs.getInt("total_count"),
                rs.getInt("completed_count"),
                rs.getInt("failed_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")));
    }

    private CreateRequest readSettings(String json, String mode, String dryRunJobId, String fingerprint) {
        try {
            Settings settings = objectMapper.readValue(json, Settings.class);
            return new CreateRequest(Mode.valueOf(mode), settings, MAX_LIMIT, dryRunJobId, fingerprint);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored metadata backfill settings are invalid", ex);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Metadata backfill settings are invalid", ex);
        }
    }

    private static String fingerprint(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 1000 : limit, MAX_LIMIT));
    }

    private static String safePrincipal(String principal) {
        return principal == null || principal.isBlank() ? "anonymous" : principal.substring(0, Math.min(200, principal.length()));
    }

    private static String safeCode(RuntimeException ex) {
        return ex.getClass().getSimpleName().substring(0, Math.min(100, ex.getClass().getSimpleName().length()));
    }

    private static boolean same(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Map<String, Object> nullableMap(Object... values) {
        java.util.HashMap<String, Object> result = new java.util.HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i].toString(), values[i + 1]);
        }
        return result;
    }

    public enum Mode {
        DRY_RUN,
        APPLY
    }

    public record Settings(String documentSemanticType, String metadataEnrichmentMode) {
    }

    public record CreateRequest(
            Mode mode,
            Settings settings,
            int limit,
            String dryRunJobId,
            String settingsFingerprint) {

        static CreateRequest defaults() {
            return new CreateRequest(Mode.DRY_RUN, new Settings("AUTO", "AUTO"), 1000, null, null);
        }

        CreateRequest normalized() {
            return new CreateRequest(
                    mode == null ? Mode.DRY_RUN : mode,
                    settings == null ? new Settings("AUTO", "AUTO") : settings,
                    boundedLimit(limit),
                    blankToNull(dryRunJobId),
                    blankToNull(settingsFingerprint));
        }
    }

    public record JobView(
            String jobId,
            String mode,
            String status,
            String requestedBy,
            String settingsJson,
            String settingsFingerprint,
            String dryRunJobId,
            boolean cancelRequested,
            int totalCount,
            int completedCount,
            int failedCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {
    }

    public record ItemView(
            String itemId,
            String jobId,
            String documentId,
            String revisionId,
            String contentHash,
            String status,
            String artifactFingerprint,
            String errorCode) {
    }
}
