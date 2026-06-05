package studio.one.platform.skillgraph.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJob;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJobStatus;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;

@Slf4j
public class DefaultSkillRagExtractionJobService implements SkillRagExtractionJobService {

    private static final String RAG_CHUNK_SOURCE_TYPE = "RAG_CHUNK";
    private static final int DEFAULT_JOB_LIMIT = 50;
    private static final int MAX_JOB_LIMIT = 200;
    private static final int DEFAULT_ITEM_LIMIT = 100;
    private static final int MAX_ITEM_LIMIT = 500;
    private static final String LEGACY_GENERIC_ATTACHMENT_OBJECT_TYPE = "2001";
    private static final String ATTACHMENT_OBJECT_TYPE = "attachment";

    private final SkillExtractionService extractionService;
    private final SkillGraphRagChunkResolver ragChunkResolver;
    private final SkillRagExtractionJobStore store;
    private final Executor executor;
    private final SkillRagExtractionJobSettings settings;
    private final Clock clock;
    private final ObjectTypeRuntimeService objectTypeRuntimeService;
    private final SkillCandidateReviewService candidateReviewService;

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings) {
        this(extractionService, ragChunkResolver, store, executor, settings, Clock.systemUTC(), null, null);
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            ObjectTypeRuntimeService objectTypeRuntimeService) {
        this(extractionService, ragChunkResolver, store, executor, settings, Clock.systemUTC(),
                objectTypeRuntimeService, null);
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            Clock clock) {
        this(extractionService, ragChunkResolver, store, executor, settings, clock, null, null);
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            Clock clock,
            ObjectTypeRuntimeService objectTypeRuntimeService) {
        this(extractionService, ragChunkResolver, store, executor, settings, clock, objectTypeRuntimeService, null);
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            Clock clock,
            ObjectTypeRuntimeService objectTypeRuntimeService,
            SkillCandidateReviewService candidateReviewService) {
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.ragChunkResolver = Objects.requireNonNull(ragChunkResolver, "ragChunkResolver");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectTypeRuntimeService = objectTypeRuntimeService;
        this.candidateReviewService = candidateReviewService;
    }

    @Override
    public SkillRagExtractionJob submitAllChunks(String objectType, String objectId, String documentId, Integer limit) {
        return submitAllChunks(objectType, objectId, documentId, limit, false, false, null, null, null);
    }

    @Override
    public SkillRagExtractionJob submitAllChunks(
            String objectType,
            String objectId,
            String documentId,
            Integer limit,
            boolean excludeExtracted,
            boolean generateEmbeddings,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        int requestedChunks = boundedLimit(limit);
        Instant now = clock.instant();
        SkillRagExtractionJob job = new SkillRagExtractionJob(
                "srj_" + UUID.randomUUID().toString().replace("-", ""),
                normalizeRagObjectType(objectType),
                normalize(objectId),
                normalize(documentId),
                SkillRagExtractionJobStatus.RUNNING,
                requestedChunks,
                0,
                0,
                0,
                0,
                0,
                null,
                generateEmbeddings,
                normalize(embeddingProvider),
                normalize(embeddingModel),
                embeddingDimension,
                null,
                null,
                now,
                now);
        store.saveJob(job);
        try {
            executor.execute(() -> processAllChunks(job.jobId(), Set.of(), excludeExtracted,
                    generateEmbeddings, embeddingProvider, embeddingModel, embeddingDimension));
            return job;
        } catch (RejectedExecutionException ex) {
            return store.saveJob(job.withStatus(SkillRagExtractionJobStatus.FAILED,
                    "RAG extraction job queue is full", clock.instant()));
        }
    }

    @Override
    public SkillRagExtractionJob getJob(String jobId) {
        return store.findJob(required(jobId, "jobId"))
                .map(this::reconcileJob)
                .orElseThrow(() -> new IllegalArgumentException("RAG extraction job not found: " + jobId));
    }

    @Override
    public List<SkillRagExtractionJob> listJobs(
            String status,
            String objectType,
            String objectId,
            String documentId,
            int offset,
            int limit) {
        SkillRagExtractionJobStatus parsedStatus = parseStatus(status);
        return store.listJobs(null, normalize(objectType), normalize(objectId), normalize(documentId),
                Math.max(0, offset), boundedJobLimit(limit)).stream()
                .map(this::reconcileJob)
                .filter(job -> parsedStatus == null || job.status() == parsedStatus)
                .toList();
    }

    @Override
    public List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit) {
        getJob(jobId);
        return store.listItems(required(jobId, "jobId"), Math.max(0, offset), boundedItemLimit(limit));
    }

    @Override
    public SkillRagExtractionJob retryFailed(String jobId) {
        SkillRagExtractionJob job = getJob(jobId);
        if (job.status() == SkillRagExtractionJobStatus.RUNNING || job.status() == SkillRagExtractionJobStatus.READY) {
            throw new IllegalStateException("RAG extraction job is still active: " + jobId);
        }
        List<SkillRagExtractionJobItem> failedItems = store.listItemsByStatus(
                job.jobId(), SkillRagExtractionItemStatus.FAILED, settings.maxChunks());
        if (failedItems.isEmpty() && job.status() != SkillRagExtractionJobStatus.FAILED) {
            return job;
        }
        Set<String> chunkIds = new HashSet<>();
        for (SkillRagExtractionJobItem item : failedItems) {
            chunkIds.add(item.chunkId());
        }
        SkillRagExtractionJob running = store.saveJob(job.withStatus(SkillRagExtractionJobStatus.RUNNING, null,
                clock.instant()));
        try {
            executor.execute(() -> processAllChunks(job.jobId(), chunkIds, false, false, null, null, null));
            return running;
        } catch (RejectedExecutionException ex) {
            return store.saveJob(running.withStatus(SkillRagExtractionJobStatus.FAILED,
                    "RAG extraction job queue is full", clock.instant()));
        }
    }

    private void processAllChunks(
            String jobId,
            Set<String> retryChunkIds,
            boolean excludeExtracted,
            boolean generateEmbeddings,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        SkillRagExtractionJob job = getJob(jobId);
        String ragObjectType = normalizeRagObjectType(job.objectType());
        Set<String> alreadyExtracted = excludeExtracted ? successfulChunkIds(job) : Set.of();
        int offset = 0;
        int total = retryChunkIds.isEmpty()
                ? effectiveTargetChunks(ragObjectType, job.objectId(), job.requestedChunks(), alreadyExtracted)
                : job.totalChunks();
        int processed = retryChunkIds.isEmpty() ? 0 : job.processedChunks() - retryChunkIds.size();
        int succeeded = retryChunkIds.isEmpty() ? 0 : job.succeededChunks();
        int failed = retryChunkIds.isEmpty() ? 0 : Math.max(0, job.failedChunks() - retryChunkIds.size());
        int extracted = retryChunkIds.isEmpty() ? 0 : job.extractedCount();
        try {
            if (retryChunkIds.isEmpty()) {
                store.saveJob(job.withProgress(SkillRagExtractionJobStatus.RUNNING, total, processed, succeeded,
                        failed, extracted, null, clock.instant()));
            }
            while (processed < total) {
                List<ResolvedRagChunk> fetched = ragChunkResolver.listByObject(ragObjectType, job.objectId(), offset,
                        settings.batchSize());
                if (fetched.isEmpty()) {
                    break;
                }
                offset += fetched.size();
                List<ResolvedRagChunk> batch = eligibleBatch(job, fetched, retryChunkIds, alreadyExtracted);
                if (batch.isEmpty() && fetched.size() < settings.batchSize()) {
                    break;
                }
                for (ResolvedRagChunk chunk : batch) {
                    if (processed >= total) {
                        break;
                    }
                    SkillRagExtractionJobItem item = extract(job, chunk);
                    processed++;
                    if (item.status() == SkillRagExtractionItemStatus.SUCCEEDED) {
                        succeeded++;
                        extracted += item.extractedCount();
                    } else {
                        failed++;
                    }
                    store.saveJob(job.withProgress(
                            SkillRagExtractionJobStatus.RUNNING,
                            total,
                            processed,
                            succeeded,
                            failed,
                            extracted,
                            null,
                            clock.instant()));
                }
                if (fetched.size() < settings.batchSize()) {
                    break;
                }
            }
            SkillRagExtractionJobStatus status = total == 0 && excludeExtracted
                    ? SkillRagExtractionJobStatus.COMPLETED
                    : finalStatus(processed, succeeded, failed);
            SkillRagExtractionJob completed = store.saveJob(job.withProgress(status, total, processed, succeeded,
                    failed, extracted, null, clock.instant()));
            if (status == SkillRagExtractionJobStatus.COMPLETED && completed.generateEmbeddings()) {
                startCandidateEmbedding(completed);
            }
        } catch (RuntimeException ex) {
            log.warn("SkillGraph RAG extraction job failed: {}", jobId, ex);
            store.saveJob(job.withProgress(SkillRagExtractionJobStatus.FAILED, total, processed, succeeded, failed,
                    extracted, "RAG extraction job failed", clock.instant()));
        }
    }

    private int effectiveTargetChunks(
            String objectType,
            String objectId,
            int requestedChunks,
            Set<String> alreadyExtracted) {
        long available = Math.max(0L, ragChunkResolver.countByObject(objectType, objectId) - alreadyExtracted.size());
        long requested = Math.max(0, requestedChunks);
        long target = Math.min(available, requested);
        return target > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) target;
    }

    private List<ResolvedRagChunk> eligibleBatch(
            SkillRagExtractionJob job,
            List<ResolvedRagChunk> fetched,
            Set<String> retryChunkIds,
            Set<String> alreadyExtracted) {
        List<ResolvedRagChunk> batch = new ArrayList<>();
        for (ResolvedRagChunk chunk : fetched) {
            if (chunk.content() == null || chunk.content().isBlank()) {
                continue;
            }
            if (job.documentId() != null && !job.documentId().equals(chunk.documentId())) {
                continue;
            }
            if (!retryChunkIds.isEmpty() && !retryChunkIds.contains(chunk.chunkId())) {
                continue;
            }
            if (retryChunkIds.isEmpty() && alreadyExtracted.contains(chunk.chunkId())) {
                continue;
            }
            batch.add(chunk);
        }
        return batch;
    }

    private Set<String> successfulChunkIds(SkillRagExtractionJob job) {
        return store.findSuccessfulChunkIds(
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.jobId());
    }

    private SkillRagExtractionJobItem extract(SkillRagExtractionJob job, ResolvedRagChunk chunk) {
        Instant now = clock.instant();
        String sourceId = Optional.ofNullable(chunk.documentId())
                .orElseGet(() -> Optional.ofNullable(chunk.objectId()).orElse(job.objectId()));
        if (chunk.content().getBytes(StandardCharsets.UTF_8).length > settings.maxTextBytesPerBatch()) {
            return store.saveItem(new SkillRagExtractionJobItem(job.jobId(), chunk.chunkId(), chunk.documentId(),
                    sourceId, null, 0, SkillRagExtractionItemStatus.FAILED,
                    "RAG chunk text exceeds maxTextBytesPerBatch", now, now));
        }
        try {
            SkillExtractionResult result = extractionService.extract(new SkillExtractionCommand(
                    RAG_CHUNK_SOURCE_TYPE,
                    sourceId,
                    chunk.chunkId(),
                    chunk.content()));
            return store.saveItem(new SkillRagExtractionJobItem(job.jobId(), chunk.chunkId(), chunk.documentId(),
                    sourceId, result.sourceChunkId(), result.extractedCount(), SkillRagExtractionItemStatus.SUCCEEDED,
                    null, now, now));
        } catch (RuntimeException ex) {
            return store.saveItem(new SkillRagExtractionJobItem(job.jobId(), chunk.chunkId(), chunk.documentId(),
                    sourceId, null, 0, SkillRagExtractionItemStatus.FAILED, failureMessage(ex), now, now));
        }
    }

    private SkillRagExtractionJobStatus finalStatus(int processed, int succeeded, int failed) {
        if (processed == 0) {
            return SkillRagExtractionJobStatus.FAILED;
        }
        if (failed > 0 && succeeded > 0) {
            return SkillRagExtractionJobStatus.PARTIAL;
        }
        if (failed > 0) {
            return SkillRagExtractionJobStatus.FAILED;
        }
        return SkillRagExtractionJobStatus.COMPLETED;
    }

    private void startCandidateEmbedding(SkillRagExtractionJob job) {
        if (candidateReviewService == null) {
            return;
        }
        String provider = normalize(job.embeddingProvider());
        String model = normalize(job.embeddingModel());
        if (provider == null || model == null) {
            return;
        }
        try {
            SkillDictionaryEmbeddingResult result = candidateReviewService.embedMissing(provider, model,
                    job.embeddingDimension() == null ? 0 : job.embeddingDimension(), settings.maxChunks());
            SkillRagExtractionJobStatus status = switch (result.status()) {
                case FAILED -> SkillRagExtractionJobStatus.FAILED;
                case PARTIAL -> SkillRagExtractionJobStatus.PARTIAL;
                case COMPLETED -> SkillRagExtractionJobStatus.COMPLETED;
                case READY, RUNNING -> SkillRagExtractionJobStatus.RUNNING;
            };
            store.saveJob(job.withEmbeddingJob(status, result.jobId(), result.status().name(), result.message(),
                    clock.instant()));
        } catch (RuntimeException ex) {
            store.saveJob(job.withEmbeddingJob(SkillRagExtractionJobStatus.FAILED, null, "FAILED",
                    failureMessage(ex), clock.instant()));
        }
    }

    private SkillRagExtractionJob reconcileJob(SkillRagExtractionJob job) {
        SkillRagExtractionJob reconciled = reconcileCompletedActiveJob(job);
        if (reconciled.embeddingJobId() == null || candidateReviewService == null) {
            return reconciled;
        }
        try {
            SkillDictionaryEmbeddingJob embeddingJob = candidateReviewService.getEmbeddingJob(reconciled.embeddingJobId());
            SkillRagExtractionJobStatus status = switch (embeddingJob.status()) {
                case COMPLETED -> SkillRagExtractionJobStatus.COMPLETED;
                case PARTIAL -> SkillRagExtractionJobStatus.PARTIAL;
                case FAILED -> SkillRagExtractionJobStatus.FAILED;
                case READY, RUNNING -> SkillRagExtractionJobStatus.RUNNING;
            };
            if (status == reconciled.status()
                    && embeddingJob.status().name().equals(reconciled.embeddingStatus())) {
                return reconciled;
            }
            return store.saveJob(reconciled.withEmbeddingJob(status, embeddingJob.jobId(),
                    embeddingJob.status().name(), embeddingJob.message(), clock.instant()));
        } catch (RuntimeException ex) {
            return reconciled;
        }
    }

    private SkillRagExtractionJob reconcileCompletedActiveJob(SkillRagExtractionJob job) {
        if (job.status() != SkillRagExtractionJobStatus.RUNNING && job.status() != SkillRagExtractionJobStatus.READY) {
            return job;
        }
        if (job.totalChunks() <= 0 || job.processedChunks() < job.totalChunks()) {
            return job;
        }
        SkillRagExtractionJobStatus status = finalStatus(
                job.processedChunks(),
                job.succeededChunks(),
                job.failedChunks());
        if (status == job.status()) {
            return job;
        }
        return store.saveJob(job.withStatus(status, null, clock.instant()));
    }

    private int boundedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return settings.maxChunks();
        }
        return Math.min(limit, settings.maxChunks());
    }

    private int boundedJobLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_JOB_LIMIT;
        }
        return Math.min(limit, MAX_JOB_LIMIT);
    }

    private int boundedItemLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_ITEM_LIMIT;
        }
        return Math.min(limit, MAX_ITEM_LIMIT);
    }

    private String failureMessage(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException && ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "Skill extraction failed";
    }

    private String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeRagObjectType(String objectType) {
        String normalized = required(objectType, "objectType");
        if (isInteger(normalized)) {
            String code = resolveObjectTypeCode(Integer.parseInt(normalized));
            if (code != null) {
                return code;
            }
        }
        return LEGACY_GENERIC_ATTACHMENT_OBJECT_TYPE.equals(normalized) ? ATTACHMENT_OBJECT_TYPE : normalized;
    }

    private String resolveObjectTypeCode(int objectType) {
        if (objectTypeRuntimeService == null) {
            return null;
        }
        try {
            ObjectTypeDefinition definition = objectTypeRuntimeService.definition(objectType);
            if (definition == null || definition.type() == null) {
                return null;
            }
            return normalize(definition.type().code());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isInteger(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SkillRagExtractionJobStatus parseStatus(String status) {
        String normalized = normalize(status);
        return normalized == null ? null : SkillRagExtractionJobStatus.valueOf(normalized.toUpperCase(java.util.Locale.ROOT));
    }
}
