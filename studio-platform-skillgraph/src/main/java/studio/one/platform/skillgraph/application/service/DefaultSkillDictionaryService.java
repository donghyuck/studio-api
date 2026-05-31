package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJob;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJobStatus;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;
import studio.one.platform.skillgraph.domain.port.SkillTaxonomyStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphBatchJobStore;

/**
 * 스킬 사전 조회 유스케이스 구현체.
 *
 * 주요 역할:
 * - Skill Dictionary 검색
 * - Skill 상세 조회
 * - alias 포함 검색 지원
 * - category/taxonomy 기반 조회
 *
 * 핵심 처리 흐름:
 * 1. 검색어 normalize
 * 2. exact/alias 기준 검색
 * 3. category/taxonomy 조건 적용
 * 4. 결과 정렬 및 반환
 *
 * Skill Extraction, Recommendation, Mapping 기능의
 * 기준 데이터 조회 계층으로 사용된다.
 *
 * @author donghyuck, son
 * @since 2026-05-17
 *
 *        <pre>
 *
 * &lt;&lt; 개정이력(Modification Information) &gt;&gt;
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-05-17  donghyuck, son: 최초 생성.
 *        </pre>
 */

public class DefaultSkillDictionaryService implements SkillDictionaryService {

    private final SkillDictionaryStore store;
    private final SkillTaxonomyStore taxonomyStore;
    private final SkillEmbeddingPort embeddingPort;
    private final Executor embeddingJobExecutor;
    private final SkillGraphBatchJobStore jobStore;
    private final SkillGraphBatchJobNotifier jobNotifier;

    public DefaultSkillDictionaryService(SkillDictionaryStore store) {
        this(store, new NoOpSkillEmbeddingPort());
    }

    public DefaultSkillDictionaryService(SkillDictionaryStore store, SkillEmbeddingPort embeddingPort) {
        this(store, null, embeddingPort, Runnable::run);
    }

    public DefaultSkillDictionaryService(
            SkillDictionaryStore store,
            SkillEmbeddingPort embeddingPort,
            Executor embeddingJobExecutor) {
        this(store, null, embeddingPort, embeddingJobExecutor);
    }

    public DefaultSkillDictionaryService(
            SkillDictionaryStore store,
            SkillTaxonomyStore taxonomyStore,
            SkillEmbeddingPort embeddingPort,
            Executor embeddingJobExecutor) {
        this(store, taxonomyStore, embeddingPort, embeddingJobExecutor,
                new InMemorySkillGraphBatchJobStore(), SkillGraphBatchJobNotifier.NOOP);
    }

    public DefaultSkillDictionaryService(
            SkillDictionaryStore store,
            SkillTaxonomyStore taxonomyStore,
            SkillEmbeddingPort embeddingPort,
            Executor embeddingJobExecutor,
            SkillGraphBatchJobStore jobStore,
            SkillGraphBatchJobNotifier jobNotifier) {
        this.store = store;
        this.taxonomyStore = taxonomyStore;
        this.embeddingPort = embeddingPort == null ? new NoOpSkillEmbeddingPort() : embeddingPort;
        this.embeddingJobExecutor = embeddingJobExecutor == null ? Runnable::run : embeddingJobExecutor;
        this.jobStore = jobStore == null ? new InMemorySkillGraphBatchJobStore() : jobStore;
        this.jobNotifier = jobNotifier == null ? SkillGraphBatchJobNotifier.NOOP : jobNotifier;
    }

    @Override
    public Page<SkillDictionaryView> search(String q, String status, String categoryId, Pageable pageable) {
        return store.search(q, status, categoryId, pageable)
                .map(this::toView);
    }

    @Override
    public SkillDictionaryView create(CreateSkillDictionaryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String name = requireText(command.name(), "name");
        String normalizedName = SkillCandidate.normalizeSkillTerm(
                command.normalizedName() == null || command.normalizedName().isBlank()
                        ? name
                        : command.normalizedName());
        store.findByNormalizedName(normalizedName)
                .ifPresent(existing -> {
                    throw new DuplicateSkillDictionaryException(normalizedName);
                });
        Instant now = Instant.now();
        SkillDictionary skill = new SkillDictionary(
                "skill_" + UUID.randomUUID(),
                name,
                normalizedName,
                normalize(command.categoryId()),
                normalize(command.status()),
                now,
                now);
        return toView(store.save(skill));
    }

    @Override
    public SkillDictionaryView get(String skillId) {
        return store.findById(skillId)
                .map(this::toView)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
    }

    @Override
    public SkillDictionaryEmbeddingResult embedMissing(
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            int limit) {
        String provider = embeddingProvider == null || embeddingProvider.isBlank() ? null : embeddingProvider.trim();
        String model = embeddingModel == null || embeddingModel.isBlank() ? null : embeddingModel.trim();
        int dimension = embeddingDimension <= 0 ? 0 : embeddingDimension;
        int max = normalizeLimit(limit);
        int totalMissing = store.countMissingEmbeddingSkills(provider, model);
        List<SkillDictionary> missing = store.findMissingEmbeddingSkills(provider, model, max);
        String jobId = "skill_dictionary_embedding_" + UUID.randomUUID();
        Instant now = Instant.now();
        SkillGraphBatchJob job = new SkillGraphBatchJob(
                jobId,
                SkillGraphBatchJobType.DICTIONARY_EMBEDDING,
                SkillGraphBatchJobStatus.CREATED,
                totalMissing,
                missing.size(),
                0,
                0,
                0,
                Math.max(0, totalMissing - missing.size()),
                provider,
                model,
                dimension,
                "{\"limit\":" + max + "}",
                "Dictionary embedding job is queued",
                null,
                now,
                null,
                now,
                null);
        saveJob(job);
        try {
            embeddingJobExecutor.execute(() -> runEmbeddingJob(jobId, missing, totalMissing, provider, model, dimension));
        } catch (RejectedExecutionException ex) {
            Instant failedAt = Instant.now();
            SkillGraphBatchJob failedJob = job.withProgress(
                    SkillGraphBatchJobStatus.FAILED,
                    0,
                    0,
                    missing.size(),
                    Math.max(0, totalMissing - missing.size()),
                    "Embedding job queue is full",
                    failedAt,
                    failedAt);
            saveJob(failedJob);
            return resultFrom(toEmbeddingJob(failedJob));
        }
        return resultFrom(toEmbeddingJob(currentJob(jobId).orElse(job)));
    }

    @Override
    public SkillDictionaryEmbeddingJob getEmbeddingJob(String jobId) {
        String normalizedJobId = requireText(jobId, "jobId");
        return currentJob(normalizedJobId)
                .map(this::toEmbeddingJob)
                .orElseThrow(() -> new IllegalArgumentException("Unknown embedding job: " + normalizedJobId));
    }

    private void runEmbeddingJob(
            String jobId,
            List<SkillDictionary> missing,
            int totalMissing,
            String provider,
            String model,
            int dimension) {
        Instant startedAt = Instant.now();
        currentJob(jobId)
                .map(job -> job.markStarted(startedAt, "Dictionary embedding job is running"))
                .ifPresent(this::saveJob);
        int processed = 0;
        int failed = 0;
        int skipped = Math.max(0, totalMissing - missing.size());
        String lastFailureMessage = null;
        for (SkillDictionary skill : missing) {
            try {
                List<Double> embedding = embeddingPort.embedSkill(skill.name(), provider, model);
                if (embedding == null || embedding.isEmpty()) {
                    failed++;
                    lastFailureMessage = "Embedding provider returned an empty vector";
                    updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, lastFailureMessage);
                    continue;
                }
                if (dimension > 0 && embedding.size() != dimension) {
                    throw new IllegalStateException("Embedding dimension " + embedding.size()
                            + " does not match requested dimension " + dimension);
                }
                store.saveEmbedding(skill.skillId(), provider, model, embedding);
                processed++;
                updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, null);
            } catch (RuntimeException ex) {
                failed++;
                lastFailureMessage = ex.getMessage();
                updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, lastFailureMessage);
            }
        }
        SkillGraphBatchJobStatus status = failed == 0
                ? SkillGraphBatchJobStatus.COMPLETED
                : processed > 0 ? SkillGraphBatchJobStatus.PARTIAL : SkillGraphBatchJobStatus.FAILED;
        Instant completedAt = Instant.now();
        String message = failed == 0 ? "Embedding job completed"
                : "Embedding job completed with failures"
                        + (lastFailureMessage == null || lastFailureMessage.isBlank() ? "" : ": " + lastFailureMessage);
        int finalProcessed = processed;
        int finalFailed = failed;
        currentJob(jobId)
                .map(job -> job.withProgress(status, finalProcessed, 0, finalFailed, skipped, message, completedAt,
                        completedAt))
                .ifPresent(this::saveJob);
    }

    private void updateEmbeddingJob(
            String jobId,
            List<SkillDictionary> missing,
            int totalMissing,
            int processed,
            int failed,
            String message) {
        Instant now = Instant.now();
        int skipped = Math.max(0, totalMissing - missing.size());
        currentJob(jobId)
                .map(job -> job.withProgress(SkillGraphBatchJobStatus.RUNNING, processed, 0, failed,
                        skipped,
                        message == null || message.isBlank() ? "Embedding job is running" : message,
                        now,
                        null))
                .ifPresent(this::saveJob);
    }

    private Optional<SkillGraphBatchJob> currentJob(String jobId) {
        return jobStore.findById(jobId);
    }

    private SkillGraphBatchJob saveJob(SkillGraphBatchJob job) {
        SkillGraphBatchJob saved = jobStore.save(job);
        jobNotifier.notifyJob(saved);
        return saved;
    }

    private SkillDictionaryEmbeddingJob toEmbeddingJob(SkillGraphBatchJob job) {
        return new SkillDictionaryEmbeddingJob(
                job.jobId(),
                toEmbeddingStatus(job.status()),
                Math.toIntExact(job.totalCount()),
                Math.toIntExact(job.requestedCount()),
                Math.toIntExact(job.processedCount()),
                Math.toIntExact(job.failedCount()),
                Math.toIntExact(job.skippedCount()),
                job.startedAt() == null ? job.createdAt() : job.startedAt(),
                job.updatedAt(),
                job.completedAt(),
                job.errorMessage());
    }

    private SkillDictionaryEmbeddingJobStatus toEmbeddingStatus(SkillGraphBatchJobStatus status) {
        return switch (status) {
            case COMPLETED -> SkillDictionaryEmbeddingJobStatus.COMPLETED;
            case PARTIAL -> SkillDictionaryEmbeddingJobStatus.PARTIAL;
            case FAILED, CANCELED -> SkillDictionaryEmbeddingJobStatus.FAILED;
            case RUNNING, VALIDATING -> SkillDictionaryEmbeddingJobStatus.RUNNING;
            case CREATED -> SkillDictionaryEmbeddingJobStatus.READY;
        };
    }

    private SkillDictionaryEmbeddingResult resultFrom(SkillDictionaryEmbeddingJob job) {
        return new SkillDictionaryEmbeddingResult(
                job.totalCount(),
                job.requestedCount(),
                job.processedCount(),
                job.skippedCount(),
                job.failedCount(),
                job.jobId(),
                job.status(),
                job.message());
    }

    private SkillDictionaryView toView(SkillDictionary skill) {
        String categoryName = skill.categoryId() == null || taxonomyStore == null ? null
                : taxonomyStore.findCategory(skill.categoryId())
                        .map(category -> category.name())
                        .orElse(null);
        return SkillDictionaryView.from(skill, categoryName, store.findEmbeddingMetadataList(skill.skillId()));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, SkillGraphLimits.MAX_SEARCH_LIMIT);
    }

    private String normalizeQuery(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim();
        return trimmed.length() <= SkillGraphLimits.MAX_QUERY_LENGTH
                ? trimmed
                : trimmed.substring(0, SkillGraphLimits.MAX_QUERY_LENGTH);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
