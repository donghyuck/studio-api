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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.ResolvedRagChunk;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
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
import studio.one.platform.skillgraph.application.usecase.SkillRagExtractionJobNotifier;
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
    private final SkillRagExtractionJobNotifier jobNotifier;
    private final String leaseOwner = UUID.randomUUID().toString();

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
        this(extractionService, ragChunkResolver, store, executor, settings, clock, objectTypeRuntimeService,
                candidateReviewService, SkillRagExtractionJobNotifier.NOOP);
    }

    public DefaultSkillRagExtractionJobService(
            SkillExtractionService extractionService,
            SkillGraphRagChunkResolver ragChunkResolver,
            SkillRagExtractionJobStore store,
            Executor executor,
            SkillRagExtractionJobSettings settings,
            Clock clock,
            ObjectTypeRuntimeService objectTypeRuntimeService,
            SkillCandidateReviewService candidateReviewService,
            SkillRagExtractionJobNotifier jobNotifier) {
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
        this.ragChunkResolver = Objects.requireNonNull(ragChunkResolver, "ragChunkResolver");
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectTypeRuntimeService = objectTypeRuntimeService;
        this.candidateReviewService = candidateReviewService;
        this.jobNotifier = jobNotifier == null ? SkillRagExtractionJobNotifier.NOOP : jobNotifier;
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
        return submit(objectType, objectId, documentId, null, List.of(), limit, excludeExtracted,
                generateEmbeddings, embeddingProvider, embeddingModel, embeddingDimension);
    }

    @Override
    public SkillRagExtractionJob submit(
            String objectType,
            String objectId,
            String documentId,
            String query,
            List<String> chunkIds,
            Integer limit,
            boolean excludeExtracted,
            boolean generateEmbeddings,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        List<String> selectedChunkIds = chunkIds == null ? List.of() : chunkIds.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .limit(settings.maxChunks())
                .toList();
        int requestedChunks = boundedLimit(limit);
        Instant now = clock.instant();
        SkillRagExtractionJob job = new SkillRagExtractionJob(
                "srj_" + UUID.randomUUID().toString().replace("-", ""),
                normalizeRagObjectType(objectType),
                normalize(objectId),
                normalize(documentId),
                normalize(query),
                selectedChunkIds.isEmpty() ? "ALL_CHUNKS" : "SELECTED_CHUNKS",
                selectedChunkIds,
                SkillRagExtractionJobStatus.RUNNING,
                selectedChunkIds.isEmpty() ? requestedChunks : Math.min(requestedChunks, selectedChunkIds.size()),
                0,
                0,
                0,
                0,
                0,
                null,
                excludeExtracted,
                generateEmbeddings,
                normalize(embeddingProvider),
                normalize(embeddingModel),
                embeddingDimension,
                null,
                null,
                now,
                now);
        saveJobAndNotify(job);
        try {
            submitLeased(job.jobId(), Set.of(), false);
            return store.findJob(job.jobId()).orElse(job);
        } catch (RejectedExecutionException ex) {
            return saveJobAndNotify(job.withStatus(SkillRagExtractionJobStatus.FAILED,
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
    public Page<SkillRagExtractionJob> searchJobs(
            String status,
            String objectType,
            String objectId,
            String documentId,
            Pageable pageable) {
        SkillRagExtractionJobStatus parsedStatus = parseStatus(status);
        return store.searchJobs(parsedStatus, normalize(objectType), normalize(objectId), normalize(documentId),
                pageable).map(this::reconcileJob);
    }

    @Override
    public List<SkillRagExtractionJobItem> listItems(String jobId, int offset, int limit) {
        getJob(jobId);
        return store.listItems(required(jobId, "jobId"), Math.max(0, offset), boundedItemLimit(limit));
    }

    @Override
    public Page<SkillCandidateView> listCandidates(String jobId, Pageable pageable) {
        String normalizedJobId = required(jobId, "jobId");
        getJob(normalizedJobId);
        if (candidateReviewService == null) {
            return Page.empty(pageable);
        }
        Set<String> sourceChunkIds = store.listItemsByStatus(
                normalizedJobId, SkillRagExtractionItemStatus.SUCCEEDED, settings.maxChunks()).stream()
                .map(SkillRagExtractionJobItem::sourceChunkId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (sourceChunkIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return candidateReviewService.searchBySourceChunkIds(sourceChunkIds, pageable);
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
        SkillRagExtractionJob running = saveJobAndNotify(job.withStatus(SkillRagExtractionJobStatus.RUNNING, null,
                clock.instant()));
        try {
            submitLeased(job.jobId(), chunkIds, true);
            return running;
        } catch (RejectedExecutionException ex) {
            return saveJobAndNotify(running.withStatus(SkillRagExtractionJobStatus.FAILED,
                    "RAG extraction job queue is full", clock.instant()));
        }
    }

    @Override
    public int recoverStaleJobs() {
        int recovered = 0;
        for (String jobId : store.findRecoverableJobIds(clock.instant(), settings.maxChunks())) {
            try {
                submitLeased(jobId, Set.of(), true);
                recovered++;
            } catch (RejectedExecutionException ex) {
                break;
            }
        }
        return recovered;
    }

    private void submitLeased(String jobId, Set<String> retryChunkIds, boolean resume) {
        Instant now = clock.instant();
        if (!store.acquireLease(jobId, leaseOwner, now, settings.leaseDuration(), settings.maxAutoRetries())) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    processAllChunks(jobId, retryChunkIds, resume);
                } finally {
                    store.releaseLease(jobId, leaseOwner);
                }
            });
        } catch (RejectedExecutionException ex) {
            store.releaseLease(jobId, leaseOwner);
            throw ex;
        }
    }

    private void processAllChunks(
            String jobId,
            Set<String> retryChunkIds,
            boolean resume) {
        SkillRagExtractionJob job = getJob(jobId);
        boolean excludeExtracted = job.excludeExtracted();
        String ragObjectType = normalizeRagObjectType(job.objectType());
        Set<String> selectedChunkIds = new HashSet<>(job.selectedChunkIds());
        Set<String> alreadyExtracted = excludeExtracted || resume ? successfulChunkIds(job) : Set.of();
        Set<String> targetChunkIds = retryChunkIds.isEmpty() ? selectedChunkIds : retryChunkIds;
        int offset = 0;
        int total = countEligibleChunks(job, ragObjectType, targetChunkIds, alreadyExtracted);
        int processed = retryChunkIds.isEmpty() ? 0 : Math.max(0, job.processedChunks() - retryChunkIds.size());
        int succeeded = retryChunkIds.isEmpty() ? 0 : job.succeededChunks();
        int failed = retryChunkIds.isEmpty() ? 0 : Math.max(0, job.failedChunks() - retryChunkIds.size());
        int extracted = retryChunkIds.isEmpty() ? 0 : job.extractedCount();
        try {
            if (retryChunkIds.isEmpty()) {
                store.saveJob(job.withProgress(SkillRagExtractionJobStatus.RUNNING, total, processed, succeeded,
                        failed, extracted, null, clock.instant()));
            }
            while (processed < total) {
                List<ResolvedRagChunk> fetched = ragChunkResolver.listByObject(
                        ragObjectType, job.objectId(), job.documentId(), job.query(), offset, settings.batchSize());
                if (fetched.isEmpty()) {
                    break;
                }
                offset += fetched.size();
                List<ResolvedRagChunk> batch = eligibleBatch(job, fetched, targetChunkIds, alreadyExtracted);
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
                    renewLeaseOrStop(jobId);
                }
                store.findJob(jobId).ifPresent(jobNotifier::notifyJob);
                if (fetched.size() < settings.batchSize()) {
                    break;
                }
            }
            SkillRagExtractionJobStatus status = total == 0 && (excludeExtracted || resume)
                    ? SkillRagExtractionJobStatus.COMPLETED
                    : finalStatus(processed, succeeded, failed);
            SkillRagExtractionJob completed = saveJobAndNotify(job.withProgress(status, total, processed, succeeded,
                    failed, extracted, null, clock.instant()));
            if (status == SkillRagExtractionJobStatus.COMPLETED && completed.generateEmbeddings()) {
                startCandidateEmbedding(completed);
            }
        } catch (LeaseLostException ex) {
            log.info("SkillGraph RAG extraction worker stopped after lease loss: {}", jobId);
        } catch (RuntimeException ex) {
            log.warn("SkillGraph RAG extraction job failed: {}", jobId, ex);
            saveJobAndNotify(job.withProgress(SkillRagExtractionJobStatus.FAILED, total, processed, succeeded, failed,
                    extracted, jobFailureMessage(ex), clock.instant()));
        }
    }

    private int countEligibleChunks(
            SkillRagExtractionJob job,
            String objectType,
            Set<String> targetChunkIds,
            Set<String> alreadyExtracted) {
        if (targetChunkIds.isEmpty() && alreadyExtracted.isEmpty()) {
            long count = ragChunkResolver.countByObject(
                    objectType, job.objectId(), job.documentId(), job.query());
            return (int) Math.min(Math.min(count, Integer.MAX_VALUE), job.requestedChunks());
        }
        int count = 0;
        int offset = 0;
        while (count < job.requestedChunks()) {
            List<ResolvedRagChunk> fetched = ragChunkResolver.listByObject(
                    objectType, job.objectId(), job.documentId(), job.query(), offset, settings.batchSize());
            if (fetched.isEmpty()) {
                break;
            }
            offset += fetched.size();
            for (ResolvedRagChunk chunk : fetched) {
                if ((targetChunkIds.isEmpty() || targetChunkIds.contains(chunk.chunkId()))
                        && !alreadyExtracted.contains(chunk.chunkId())
                        && chunk.content() != null
                        && !chunk.content().isBlank()) {
                    count++;
                    if (count >= job.requestedChunks()) {
                        break;
                    }
                }
            }
            if (fetched.size() < settings.batchSize()) {
                break;
            }
        }
        return count;
    }

    private void renewLeaseOrStop(String jobId) {
        if (!store.renewLease(jobId, leaseOwner, clock.instant(), settings.leaseDuration())) {
            throw new LeaseLostException();
        }
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
        Set<String> chunkIds = new HashSet<>(store.findSuccessfulChunkIds(
                job.objectType(),
                job.objectId(),
                job.documentId(),
                job.jobId()));
        store.listItemsByStatus(job.jobId(), SkillRagExtractionItemStatus.SUCCEEDED, settings.maxChunks()).stream()
                .map(SkillRagExtractionJobItem::chunkId)
                .filter(Objects::nonNull)
                .forEach(chunkIds::add);
        return chunkIds;
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
            saveJobAndNotify(job.withEmbeddingJob(status, result.jobId(), result.status().name(), result.message(),
                    clock.instant()));
        } catch (RuntimeException ex) {
            saveJobAndNotify(job.withEmbeddingJob(SkillRagExtractionJobStatus.FAILED, null, "FAILED",
                    failureMessage(ex), clock.instant()));
        }
    }

    private SkillRagExtractionJob saveJobAndNotify(SkillRagExtractionJob job) {
        SkillRagExtractionJob saved = store.saveJob(job);
        jobNotifier.notifyJob(saved);
        return saved;
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

    private String jobFailureMessage(RuntimeException ex) {
        String message = normalize(ex.getMessage());
        if (message == null) {
            return "RAG extraction job failed";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static final class LeaseLostException extends RuntimeException {
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
