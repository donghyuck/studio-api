package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJob;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingJobStatus;
import studio.one.platform.skillgraph.application.result.SkillDictionaryEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.domain.constants.SkillGraphLimits;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;

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
    private final SkillEmbeddingPort embeddingPort;
    private final Executor embeddingJobExecutor;
    private final Map<String, SkillDictionaryEmbeddingJob> embeddingJobs = new ConcurrentHashMap<>();

    public DefaultSkillDictionaryService(SkillDictionaryStore store) {
        this(store, new NoOpSkillEmbeddingPort());
    }

    public DefaultSkillDictionaryService(SkillDictionaryStore store, SkillEmbeddingPort embeddingPort) {
        this(store, embeddingPort, Runnable::run);
    }

    public DefaultSkillDictionaryService(
            SkillDictionaryStore store,
            SkillEmbeddingPort embeddingPort,
            Executor embeddingJobExecutor) {
        this.store = store;
        this.embeddingPort = embeddingPort == null ? new NoOpSkillEmbeddingPort() : embeddingPort;
        this.embeddingJobExecutor = embeddingJobExecutor == null ? Runnable::run : embeddingJobExecutor;
    }

    @Override
    public List<SkillDictionaryView> search(String q, int limit) {
        return search(q, 0, limit);
    }

    @Override
    public List<SkillDictionaryView> search(String q, int offset, int limit) {
        return store.search(normalizeQuery(q), Math.max(0, offset), normalizeLimit(limit)).stream()
                .map(SkillDictionaryView::from)
                .toList();
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
        return SkillDictionaryView.from(store.save(skill));
    }

    @Override
    public SkillDictionaryView get(String skillId) {
        return store.findById(skillId)
                .map(SkillDictionaryView::from)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
    }

    @Override
    public SkillDictionaryEmbeddingResult embedMissing(int limit) {
        int max = normalizeLimit(limit);
        int totalMissing = store.countMissingEmbeddingSkills();
        List<SkillDictionary> missing = store.findMissingEmbeddingSkills(max);
        String jobId = "skill_dictionary_embedding_" + UUID.randomUUID();
        Instant now = Instant.now();
        SkillDictionaryEmbeddingJob job = new SkillDictionaryEmbeddingJob(
                jobId,
                SkillDictionaryEmbeddingJobStatus.READY,
                totalMissing,
                missing.size(),
                0,
                0,
                Math.max(0, totalMissing - missing.size()),
                now,
                now,
                null,
                "Embedding job is queued");
        embeddingJobs.put(jobId, job);
        try {
            embeddingJobExecutor.execute(() -> runEmbeddingJob(jobId, missing, totalMissing));
        } catch (RejectedExecutionException ex) {
            Instant failedAt = Instant.now();
            SkillDictionaryEmbeddingJob failedJob = job.withProgress(
                    SkillDictionaryEmbeddingJobStatus.FAILED,
                    0,
                    missing.size(),
                    Math.max(0, totalMissing - missing.size()),
                    failedAt,
                    failedAt,
                    "Embedding job queue is full");
            embeddingJobs.put(jobId, failedJob);
            return resultFrom(failedJob);
        }
        return resultFrom(currentJob(jobId).orElse(job));
    }

    @Override
    public SkillDictionaryEmbeddingJob getEmbeddingJob(String jobId) {
        String normalizedJobId = requireText(jobId, "jobId");
        return currentJob(normalizedJobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown embedding job: " + normalizedJobId));
    }

    private void runEmbeddingJob(String jobId, List<SkillDictionary> missing, int totalMissing) {
        Instant startedAt = Instant.now();
        embeddingJobs.computeIfPresent(jobId,
                (ignored, job) -> job.withStatus(SkillDictionaryEmbeddingJobStatus.RUNNING,
                        "Embedding job is running", startedAt));
        int processed = 0;
        int failed = 0;
        int skipped = Math.max(0, totalMissing - missing.size());
        String lastFailureMessage = null;
        for (SkillDictionary skill : missing) {
            try {
                List<Double> embedding = embeddingPort.embedSkill(skill.name());
                if (embedding == null || embedding.isEmpty()) {
                    failed++;
                    lastFailureMessage = "Embedding provider returned an empty vector";
                    updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, lastFailureMessage);
                    continue;
                }
                store.saveEmbedding(skill.skillId(), embedding, null);
                processed++;
                updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, null);
            } catch (RuntimeException ex) {
                failed++;
                lastFailureMessage = ex.getMessage();
                updateEmbeddingJob(jobId, missing, totalMissing, processed, failed, lastFailureMessage);
            }
        }
        SkillDictionaryEmbeddingJobStatus status = failed == 0
                ? SkillDictionaryEmbeddingJobStatus.COMPLETED
                : processed > 0 ? SkillDictionaryEmbeddingJobStatus.PARTIAL : SkillDictionaryEmbeddingJobStatus.FAILED;
        Instant completedAt = Instant.now();
        String message = failed == 0 ? "Embedding job completed"
                : "Embedding job completed with failures"
                        + (lastFailureMessage == null || lastFailureMessage.isBlank() ? "" : ": " + lastFailureMessage);
        int finalProcessed = processed;
        int finalFailed = failed;
        embeddingJobs.computeIfPresent(jobId,
                (ignored, job) -> job.withProgress(status, finalProcessed, finalFailed, skipped, completedAt,
                        completedAt, message));
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
        embeddingJobs.computeIfPresent(jobId,
                (ignored, job) -> job.withProgress(SkillDictionaryEmbeddingJobStatus.RUNNING, processed, failed,
                        skipped, now, null,
                        message == null || message.isBlank() ? "Embedding job is running" : message));
    }

    private Optional<SkillDictionaryEmbeddingJob> currentJob(String jobId) {
        return Optional.ofNullable(embeddingJobs.get(jobId));
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
