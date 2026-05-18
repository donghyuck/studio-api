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
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionItemStatus;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobItem;
import studio.one.platform.skillgraph.application.result.SkillRagExtractionJobStatus;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphRagChunkResolver;
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobService;
import studio.one.platform.skillgraph.domain.port.SkillRagExtractionJobStore;

@Slf4j
public class DefaultSkillRagExtractionJobService implements SkillRagExtractionJobService {

    private static final String RAG_CHUNK_SOURCE_TYPE = "RAG_CHUNK";
    private static final int DEFAULT_JOB_LIMIT = 50;
    private static final int MAX_JOB_LIMIT = 200;
    private static final int DEFAULT_ITEM_LIMIT = 100;
    private static final int MAX_ITEM_LIMIT = 500;

    private final SkillExtractionService extractionService;
    private final SkillGraphRagChunkResolver ragChunkResolver;
    private final SkillRagExtractionJobStore store;
    private final Executor executor;
    private final SkillRagExtractionJobSettings settings;
    private final Clock clock;

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings) {
        this(extractionService, ragChunkResolver, store, executor, settings, Clock.systemUTC());
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            Clock clock) {
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.ragChunkResolver = Objects.requireNonNull(ragChunkResolver, "ragChunkResolver");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SkillRagExtractionJob submitAllChunks(String objectType, String objectId, String documentId, Integer limit) {
        int requestedChunks = boundedLimit(limit);
        Instant now = clock.instant();
        SkillRagExtractionJob job = new SkillRagExtractionJob(
                "srj_" + UUID.randomUUID().toString().replace("-", ""),
                required(objectType, "objectType"),
                required(objectId, "objectId"),
                normalize(documentId),
                SkillRagExtractionJobStatus.RUNNING,
                requestedChunks,
                0,
                0,
                0,
                0,
                0,
                null,
                now,
                now);
        store.saveJob(job);
        try {
            executor.execute(() -> processAllChunks(job.jobId(), Set.of()));
            return job;
        } catch (RejectedExecutionException ex) {
            return store.saveJob(job.withStatus(SkillRagExtractionJobStatus.FAILED,
                    "RAG extraction job queue is full", clock.instant()));
        }
    }

    @Override
    public SkillRagExtractionJob getJob(String jobId) {
        return store.findJob(required(jobId, "jobId"))
                .map(this::reconcileCompletedActiveJob)
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
                .map(this::reconcileCompletedActiveJob)
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
        if (failedItems.isEmpty()) {
            return job;
        }
        Set<String> chunkIds = new HashSet<>();
        for (SkillRagExtractionJobItem item : failedItems) {
            chunkIds.add(item.chunkId());
        }
        SkillRagExtractionJob running = store.saveJob(job.withStatus(SkillRagExtractionJobStatus.RUNNING, null,
                clock.instant()));
        try {
            executor.execute(() -> processAllChunks(job.jobId(), chunkIds));
            return running;
        } catch (RejectedExecutionException ex) {
            return store.saveJob(running.withStatus(SkillRagExtractionJobStatus.FAILED,
                    "RAG extraction job queue is full", clock.instant()));
        }
    }

    private void processAllChunks(String jobId, Set<String> retryChunkIds) {
        SkillRagExtractionJob job = getJob(jobId);
        int offset = 0;
        int total = retryChunkIds.isEmpty() ? 0 : job.totalChunks();
        int processed = retryChunkIds.isEmpty() ? 0 : job.processedChunks() - retryChunkIds.size();
        int succeeded = retryChunkIds.isEmpty() ? 0 : job.succeededChunks();
        int failed = retryChunkIds.isEmpty() ? 0 : Math.max(0, job.failedChunks() - retryChunkIds.size());
        int extracted = retryChunkIds.isEmpty() ? 0 : job.extractedCount();
        try {
            while (processed < job.requestedChunks()) {
                List<ResolvedRagChunk> fetched = ragChunkResolver.listByObject(
                        job.objectType(), job.objectId(), offset, settings.batchSize());
                if (fetched.isEmpty()) {
                    break;
                }
                offset += fetched.size();
                List<ResolvedRagChunk> batch = eligibleBatch(job, fetched, retryChunkIds);
                if (batch.isEmpty() && fetched.size() < settings.batchSize()) {
                    break;
                }
                for (ResolvedRagChunk chunk : batch) {
                    if (processed >= job.requestedChunks()) {
                        break;
                    }
                    total = retryChunkIds.isEmpty() ? total + 1 : total;
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
            SkillRagExtractionJobStatus status = finalStatus(processed, succeeded, failed);
            store.saveJob(job.withProgress(status, total, processed, succeeded, failed, extracted, null, clock.instant()));
        } catch (RuntimeException ex) {
            log.warn("SkillGraph RAG extraction job failed: {}", jobId, ex);
            store.saveJob(job.withProgress(SkillRagExtractionJobStatus.FAILED, total, processed, succeeded, failed,
                    extracted, "RAG extraction job failed", clock.instant()));
        }
    }

    private List<ResolvedRagChunk> eligibleBatch(
            SkillRagExtractionJob job,
            List<ResolvedRagChunk> fetched,
            Set<String> retryChunkIds) {
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
            batch.add(chunk);
        }
        return batch;
    }

    private SkillRagExtractionJobItem extract(SkillRagExtractionJob job, ResolvedRagChunk chunk) {
        Instant now = clock.instant();
        String sourceId = Optional.ofNullable(chunk.documentId()).orElse(job.objectId());
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SkillRagExtractionJobStatus parseStatus(String status) {
        String normalized = normalize(status);
        return normalized == null ? null : SkillRagExtractionJobStatus.valueOf(normalized.toUpperCase(java.util.Locale.ROOT));
    }
}
