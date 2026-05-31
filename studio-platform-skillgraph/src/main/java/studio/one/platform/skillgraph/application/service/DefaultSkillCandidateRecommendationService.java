package studio.one.platform.skillgraph.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.SkillCandidateRecommendationJobCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillRecommendationApplyCommand;
import studio.one.platform.skillgraph.application.result.SkillRecommendationApplyResult;
import studio.one.platform.skillgraph.application.result.SkillRecommendationApplySkip;
import studio.one.platform.skillgraph.application.result.SkillRecommendationJobView;
import studio.one.platform.skillgraph.application.result.SkillRecommendationResultView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateRecommendationService;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobNotifier;
import studio.one.platform.skillgraph.domain.model.SkillCandidate;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.domain.model.SkillDictionary;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationJob;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResult;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResultStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationTargetHit;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationType;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillGraphBatchJobStore;
import studio.one.platform.skillgraph.domain.port.SkillRecommendationStore;
import studio.one.platform.skillgraph.infrastructure.persistence.memory.InMemorySkillGraphBatchJobStore;

public class DefaultSkillCandidateRecommendationService implements SkillCandidateRecommendationService {

    private static final String SOURCE_CANDIDATE = "SKILL_CANDIDATE";
    private static final String TARGET_DICTIONARY = "SKILL_DICTIONARY";
    private static final String TARGET_DATASET_CONCEPT = "DATASET_CONCEPT";

    private final SkillRecommendationStore recommendationStore;
    private final SkillCandidateStore candidateStore;
    private final SkillDictionaryStore dictionaryStore;
    private final SkillCandidateReviewService reviewService;
    private final Executor jobExecutor;
    private final SkillGraphBatchJobStore jobStore;
    private final SkillGraphBatchJobNotifier jobNotifier;

    public DefaultSkillCandidateRecommendationService(
            SkillRecommendationStore recommendationStore,
            SkillCandidateStore candidateStore,
            SkillDictionaryStore dictionaryStore,
            SkillCandidateReviewService reviewService) {
        this(recommendationStore, candidateStore, dictionaryStore, reviewService, Runnable::run,
                new InMemorySkillGraphBatchJobStore(), SkillGraphBatchJobNotifier.NOOP);
    }

    public DefaultSkillCandidateRecommendationService(
            SkillRecommendationStore recommendationStore,
            SkillCandidateStore candidateStore,
            SkillDictionaryStore dictionaryStore,
            SkillCandidateReviewService reviewService,
            Executor jobExecutor,
            SkillGraphBatchJobStore jobStore,
            SkillGraphBatchJobNotifier jobNotifier) {
        this.recommendationStore = recommendationStore;
        this.candidateStore = candidateStore;
        this.dictionaryStore = dictionaryStore;
        this.reviewService = reviewService;
        this.jobExecutor = jobExecutor == null ? Runnable::run : jobExecutor;
        this.jobStore = jobStore == null ? new InMemorySkillGraphBatchJobStore() : jobStore;
        this.jobNotifier = jobNotifier == null ? SkillGraphBatchJobNotifier.NOOP : jobNotifier;
    }

    @Override
    public SkillRecommendationJobView createJob(SkillCandidateRecommendationJobCommand command) {
        SkillCandidateRecommendationJobCommand normalized = normalize(command);
        List<SkillCandidate> candidates = candidates(normalized);
        validateCandidateEmbeddings(normalized, candidates);
        Instant now = Instant.now();
        String jobId = "skill_rec_" + UUID.randomUUID();
        SkillRecommendationJob job = new SkillRecommendationJob(
                jobId,
                normalized.targetScope(),
                filterJson(normalized),
                normalized.embeddingProvider(),
                normalized.embeddingModel(),
                normalized.embeddingDimension(),
                String.join(",", normalized.targetTypes()),
                normalized.topK(),
                normalized.minScore(),
                normalized.newSkillMinConfidence(),
                normalized.existingSkillMinScore(),
                SkillRecommendationJobStatus.CREATED,
                0,
                0,
                0,
                0,
                null,
                now,
                null,
                null,
                now);
        recommendationStore.saveJob(job);
        saveBatchJob(new SkillGraphBatchJob(
                jobId,
                SkillGraphBatchJobType.CANDIDATE_RECOMMENDATION,
                SkillGraphBatchJobStatus.CREATED,
                candidates.size(),
                candidates.size(),
                0,
                0,
                0,
                0,
                normalized.embeddingProvider(),
                normalized.embeddingModel(),
                normalized.embeddingDimension(),
                filterJson(normalized),
                "Candidate recommendation job is queued",
                null,
                now,
                null,
                now,
                null));
        try {
            jobExecutor.execute(() -> runJob(job, normalized, candidates));
        } catch (RejectedExecutionException ex) {
            Instant failedAt = Instant.now();
            SkillRecommendationJob failed = new SkillRecommendationJob(
                    job.jobId(), job.targetScope(), job.targetFilter(), job.embeddingProvider(), job.embeddingModel(),
                    job.embeddingDimension(), job.targetTypes(), job.topK(), job.minScore(), job.newSkillMinConfidence(),
                    job.existingSkillMinScore(), SkillRecommendationJobStatus.FAILED, candidates.size(), 0, 0,
                    candidates.size(), "Recommendation job queue is full", job.createdAt(), null, failedAt, failedAt);
            recommendationStore.saveJob(failed);
            saveBatchJob(new SkillGraphBatchJob(
                    jobId,
                    SkillGraphBatchJobType.CANDIDATE_RECOMMENDATION,
                    SkillGraphBatchJobStatus.FAILED,
                    candidates.size(),
                    candidates.size(),
                    0,
                    0,
                    candidates.size(),
                    0,
                    normalized.embeddingProvider(),
                    normalized.embeddingModel(),
                    normalized.embeddingDimension(),
                    filterJson(normalized),
                    "Recommendation job queue is full",
                    null,
                    now,
                    null,
                    failedAt,
                    failedAt));
            return SkillRecommendationJobView.from(failed);
        }
        return SkillRecommendationJobView.from(job);
    }

    @Override
    public Page<SkillRecommendationJobView> searchJobs(Pageable pageable) {
        return recommendationStore.searchJobs(pageable).map(SkillRecommendationJobView::from);
    }

    @Override
    public SkillRecommendationJobView getJob(String jobId) {
        return SkillRecommendationJobView.from(findJob(jobId));
    }

    @Override
    public List<SkillRecommendationResultView> getJobResults(String jobId) {
        findJob(jobId);
        return recommendationStore.findResultsByJob(jobId).stream()
                .map(SkillRecommendationResultView::from)
                .toList();
    }

    @Override
    public Page<SkillRecommendationResultView> getJobResults(String jobId, Pageable pageable) {
        findJob(jobId);
        return recommendationStore.findResultsByJob(jobId, pageable).map(SkillRecommendationResultView::from);
    }

    @Override
    public List<SkillRecommendationResultView> getCandidateResults(String candidateId) {
        return recommendationStore.findResultsByCandidate(requireText(candidateId, "candidateId")).stream()
                .map(SkillRecommendationResultView::from)
                .toList();
    }

    @Override
    public SkillRecommendationApplyResult applyResult(String resultId, SkillRecommendationApplyCommand command) {
        SkillRecommendationResult result = recommendationStore.findResult(requireText(resultId, "resultId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown recommendation result: " + resultId));
        return applyResults(List.of(result), normalizeApply(command));
    }

    @Override
    public SkillRecommendationApplyResult applyJob(String jobId, SkillRecommendationApplyCommand command) {
        findJob(jobId);
        return applyResults(recommendationStore.findResultsByJob(jobId), normalizeApply(command));
    }

    private SkillRecommendationJob runJob(
            SkillRecommendationJob job,
            SkillCandidateRecommendationJobCommand command,
            List<SkillCandidate> candidates) {
        Instant startedAt = Instant.now();
        SkillRecommendationJob running = new SkillRecommendationJob(
                job.jobId(), job.targetScope(), job.targetFilter(), job.embeddingProvider(), job.embeddingModel(),
                job.embeddingDimension(), job.targetTypes(), job.topK(), job.minScore(), job.newSkillMinConfidence(),
                job.existingSkillMinScore(), SkillRecommendationJobStatus.RUNNING, 0, 0, 0, 0, null, job.createdAt(),
                startedAt, null, startedAt);
        recommendationStore.saveJob(running);
        saveBatchJob(batchFrom(running, SkillGraphBatchJobStatus.RUNNING, candidates.size(), candidates.size(),
                0, 0, 0, 0, "Candidate recommendation job is running"));
        long processed = 0;
        long failed = 0;
        long results = 0;
        for (SkillCandidate candidate : candidates) {
            try {
                results += analyzeCandidate(job, command, candidate);
                processed++;
                saveBatchJob(batchFrom(running, SkillGraphBatchJobStatus.RUNNING, candidates.size(), candidates.size(),
                        processed, results, failed, 0, "Candidate recommendation job is running"));
            } catch (RuntimeException ex) {
                failed++;
                saveBatchJob(batchFrom(running, SkillGraphBatchJobStatus.RUNNING, candidates.size(), candidates.size(),
                        processed, results, failed, 0, ex.getMessage()));
            }
        }
        Instant completedAt = Instant.now();
        SkillRecommendationJob completed = new SkillRecommendationJob(
                job.jobId(), job.targetScope(), job.targetFilter(), job.embeddingProvider(), job.embeddingModel(),
                job.embeddingDimension(), job.targetTypes(), job.topK(), job.minScore(), job.newSkillMinConfidence(),
                job.existingSkillMinScore(), failed == 0 ? SkillRecommendationJobStatus.COMPLETED : SkillRecommendationJobStatus.FAILED,
                candidates.size(), processed, results, failed, failed == 0 ? null : "Some candidates failed during analysis",
                job.createdAt(), startedAt, completedAt, completedAt);
        saveBatchJob(batchFrom(completed,
                failed == 0 ? SkillGraphBatchJobStatus.COMPLETED
                        : processed > 0 ? SkillGraphBatchJobStatus.PARTIAL : SkillGraphBatchJobStatus.FAILED,
                candidates.size(),
                candidates.size(),
                processed,
                results,
                failed,
                0,
                failed == 0 ? "Candidate recommendation job completed" : "Some candidates failed during analysis"));
        return recommendationStore.saveJob(completed);
    }

    private SkillGraphBatchJob batchFrom(
            SkillRecommendationJob job,
            SkillGraphBatchJobStatus status,
            long total,
            long requested,
            long processed,
            long results,
            long failed,
            long skipped,
            String message) {
        Instant now = Instant.now();
        return new SkillGraphBatchJob(
                job.jobId(),
                SkillGraphBatchJobType.CANDIDATE_RECOMMENDATION,
                status,
                total,
                requested,
                processed,
                results,
                failed,
                skipped,
                job.embeddingProvider(),
                job.embeddingModel(),
                job.embeddingDimension(),
                job.targetFilter(),
                message,
                null,
                job.createdAt(),
                job.startedAt(),
                now,
                status == SkillGraphBatchJobStatus.COMPLETED || status == SkillGraphBatchJobStatus.PARTIAL
                        || status == SkillGraphBatchJobStatus.FAILED ? now : null);
    }

    private SkillGraphBatchJob saveBatchJob(SkillGraphBatchJob job) {
        SkillGraphBatchJob saved = jobStore.save(job);
        jobNotifier.notifyJob(saved);
        return saved;
    }

    private int analyzeCandidate(
            SkillRecommendationJob job,
            SkillCandidateRecommendationJobCommand command,
            SkillCandidate candidate) {
        List<Double> embedding = recommendationStore.findEmbedding(
                SOURCE_CANDIDATE,
                candidate.candidateId(),
                command.embeddingProvider(),
                command.embeddingModel(),
                command.embeddingDimension());

        List<SkillRecommendationResult> generated = new ArrayList<>();
        if (command.targetTypes().contains("SKILL_DICTIONARY")) {
            generated.addAll(dictionaryResults(job, command, candidate, embedding));
        }
        if (command.targetTypes().contains("SKILL_CANDIDATE")) {
            generated.addAll(candidateResults(job, command, candidate, embedding));
        }
        if (command.targetTypes().contains("DATASET_CONCEPT")) {
            generated.addAll(datasetResults(job, command, candidate, embedding));
        }

        boolean hasStrongDictionaryMatch = generated.stream()
                .anyMatch(result -> result.recommendationType() == SkillRecommendationType.EXISTING_SKILL_MATCH);
        if (!hasStrongDictionaryMatch && candidate.confidence() >= command.newSkillMinConfidence()) {
            generated.add(saveResult(job, candidate, null, SkillRecommendationType.NEW_SKILL_CANDIDATE,
                    candidate.confidence(), "confidence >= newSkillMinConfidence"));
        } else if (generated.isEmpty()) {
            SkillRecommendationType type = candidate.confidence() < 0.60d
                    ? SkillRecommendationType.LOW_CONFIDENCE
                    : SkillRecommendationType.REVIEW_REQUIRED;
            generated.add(saveResult(job, candidate, null, type, candidate.confidence(), "no eligible recommendation"));
        }
        return generated.size();
    }

    private List<SkillRecommendationResult> dictionaryResults(
            SkillRecommendationJob job,
            SkillCandidateRecommendationJobCommand command,
            SkillCandidate candidate,
            List<Double> embedding) {
        return recommendationStore.findNearestEmbeddings(
                        TARGET_DICTIONARY,
                        command.embeddingProvider(),
                        command.embeddingModel(),
                        command.embeddingDimension(),
                        embedding,
                        null,
                        command.topK(),
                        command.minScore()).stream()
                .map(hit -> saveResult(job, candidate, hit,
                        hit.score() >= command.existingSkillMinScore()
                                ? SkillRecommendationType.EXISTING_SKILL_MATCH
                                : SkillRecommendationType.REVIEW_REQUIRED,
                        hit.score(),
                        "dictionary vector match"))
                .toList();
    }

    private List<SkillRecommendationResult> candidateResults(
            SkillRecommendationJob job,
            SkillCandidateRecommendationJobCommand command,
            SkillCandidate candidate,
            List<Double> embedding) {
        return recommendationStore.findNearestEmbeddings(
                        SOURCE_CANDIDATE,
                        command.embeddingProvider(),
                        command.embeddingModel(),
                        command.embeddingDimension(),
                        embedding,
                        candidate.candidateId(),
                        command.topK(),
                        command.minScore()).stream()
                .map(hit -> saveResult(job, candidate, hit,
                        hit.score() >= 0.94d
                                ? SkillRecommendationType.DUPLICATE_CANDIDATE
                                : SkillRecommendationType.SIMILAR_CANDIDATE,
                        hit.score(),
                        "candidate vector match"))
                .toList();
    }

    private List<SkillRecommendationResult> datasetResults(
            SkillRecommendationJob job,
            SkillCandidateRecommendationJobCommand command,
            SkillCandidate candidate,
            List<Double> embedding) {
        return recommendationStore.findNearestEmbeddings(
                        TARGET_DATASET_CONCEPT,
                        command.embeddingProvider(),
                        command.embeddingModel(),
                        command.embeddingDimension(),
                        embedding,
                        null,
                        command.topK(),
                        Math.max(command.minScore(), 0.85d)).stream()
                .map(hit -> saveResult(job, candidate, hit, SkillRecommendationType.NCS_MAPPING_CANDIDATE,
                        hit.score(), "dataset concept vector match"))
                .toList();
    }

    private SkillRecommendationResult saveResult(
            SkillRecommendationJob job,
            SkillCandidate candidate,
            SkillRecommendationTargetHit hit,
            SkillRecommendationType type,
            double score,
            String reason) {
        Instant now = Instant.now();
        SkillRecommendationResult result = new SkillRecommendationResult(
                "skill_rec_result_" + UUID.randomUUID(),
                job.jobId(),
                SOURCE_CANDIDATE,
                candidate.candidateId(),
                candidate.term(),
                hit == null ? null : hit.sourceType(),
                hit == null ? null : hit.sourceId(),
                hit == null ? null : hit.sourceText(),
                type,
                Math.max(0.0d, Math.min(1.0d, score)),
                candidate.confidence(),
                "{\"provider\":\"%s\",\"model\":\"%s\",\"dimension\":%d}".formatted(
                        job.embeddingProvider(), job.embeddingModel(), job.embeddingDimension()),
                reason,
                SkillRecommendationResultStatus.CANDIDATE,
                null,
                null,
                null,
                now);
        return recommendationStore.saveResult(result);
    }

    private SkillRecommendationApplyResult applyResults(
            List<SkillRecommendationResult> results,
            SkillRecommendationApplyCommand command) {
        List<SkillRecommendationApplySkip> skipped = new ArrayList<>();
        List<SkillRecommendationApplySkip> failed = new ArrayList<>();
        int applied = 0;
        for (SkillRecommendationResult result : results) {
            String skipReason = skipReason(result, command);
            if (skipReason != null) {
                recommendationStore.updateResultStatus(result.resultId(), SkillRecommendationResultStatus.SKIPPED,
                        null, null, skipReason);
                skipped.add(new SkillRecommendationApplySkip(result.resultId(), result.sourceId(), skipReason));
                continue;
            }
            try {
                applyOne(result);
                recommendationStore.updateResultStatus(result.resultId(), SkillRecommendationResultStatus.APPLIED,
                        result.recommendationType().name(), "system", result.reason());
                applied++;
            } catch (RuntimeException ex) {
                recommendationStore.updateResultStatus(result.resultId(), SkillRecommendationResultStatus.FAILED,
                        null, null, ex.getMessage());
                failed.add(new SkillRecommendationApplySkip(result.resultId(), result.sourceId(), ex.getMessage()));
            }
        }
        return new SkillRecommendationApplyResult(
                results.size(),
                applied,
                skipped.size(),
                failed.size(),
                skipped,
                failed);
    }

    private void applyOne(SkillRecommendationResult result) {
        if (result.recommendationType() == SkillRecommendationType.NEW_SKILL_CANDIDATE) {
            reviewService.review(result.sourceId(),
                    new SkillCandidateReviewCommand(SkillCandidateStatus.APPROVED, null, "recommendation=" + result.resultId()));
            return;
        }
        if (result.recommendationType() == SkillRecommendationType.EXISTING_SKILL_MATCH) {
            reviewService.review(result.sourceId(),
                    new SkillCandidateReviewCommand(SkillCandidateStatus.ALIAS_CANDIDATE,
                            result.targetSourceId(),
                            "recommendation=" + result.resultId() + "; similarity=" + result.similarityScore()));
            return;
        }
        throw new IllegalArgumentException("Unsupported recommendation type for apply: " + result.recommendationType());
    }

    private String skipReason(SkillRecommendationResult result, SkillRecommendationApplyCommand command) {
        if (result.status() != SkillRecommendationResultStatus.CANDIDATE) {
            return "result is not candidate";
        }
        if (!command.recommendationTypes().contains(result.recommendationType().name())) {
            return "recommendation type is not allowed";
        }
        if (!result.bulkApplicable()) {
            return "recommendation type is not bulk applicable";
        }
        SkillCandidate candidate = candidateStore.findCandidate(result.sourceId()).orElse(null);
        if (candidate == null) {
            return "candidate not found";
        }
        if (candidate.status() == SkillCandidateStatus.APPROVED
                || candidate.status() == SkillCandidateStatus.REJECTED
                || candidate.status() == SkillCandidateStatus.NOISE) {
            return "candidate already finalized";
        }
        if (result.confidence() < command.minConfidence()) {
            return "confidence below threshold";
        }
        if (result.recommendationType() == SkillRecommendationType.EXISTING_SKILL_MATCH) {
            if (result.similarityScore() < command.minSimilarityScore()) {
                return "similarity below threshold";
            }
            SkillDictionary skill = dictionaryStore.findById(result.targetSourceId()).orElse(null);
            if (skill == null) {
                return "target skill not found";
            }
        }
        return null;
    }

    private List<SkillCandidate> candidates(SkillCandidateRecommendationJobCommand command) {
        if ("SELECTED".equals(command.targetScope())) {
            return new LinkedHashSet<>(command.candidateIds()).stream()
                    .map(candidateStore::findCandidate)
                    .flatMap(Optional::stream)
                    .toList();
        }
        SkillCandidateStatus status = parseStatus(command.status());
        return candidateStore.searchCandidates(
                status,
                command.keyword(),
                command.sourceType(),
                command.sourceId(),
                Pageable.ofSize(2000)).getContent();
    }

    private void validateCandidateEmbeddings(
            SkillCandidateRecommendationJobCommand command,
            List<SkillCandidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("자동 분석 대상 후보가 없습니다.");
        }
        List<String> missing = candidates.stream()
                .filter(candidate -> recommendationStore.findEmbedding(
                        SOURCE_CANDIDATE,
                        candidate.candidateId(),
                        command.embeddingProvider(),
                        command.embeddingModel(),
                        command.embeddingDimension()).isEmpty())
                .map(SkillCandidate::candidateId)
                .limit(5)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "자동 분석은 후보 임베딩 생성 이후에 가능합니다. provider=%s, model=%s, dimension=%d 기준 누락 후보가 있습니다: %s"
                            .formatted(command.embeddingProvider(), command.embeddingModel(), command.embeddingDimension(),
                                    String.join(", ", missing)));
        }
    }

    private SkillCandidateStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return SkillCandidateStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    }

    private SkillCandidateRecommendationJobCommand normalize(SkillCandidateRecommendationJobCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        String scope = requireText(command.targetScope() == null ? "ALL" : command.targetScope(), "targetScope")
                .toUpperCase(Locale.ROOT);
        List<String> targetTypes = command.targetTypes() == null || command.targetTypes().isEmpty()
                ? List.of("SKILL_DICTIONARY", "DATASET_CONCEPT", "SKILL_CANDIDATE")
                : command.targetTypes().stream().map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        return new SkillCandidateRecommendationJobCommand(
                scope,
                command.candidateIds() == null ? List.of() : command.candidateIds(),
                command.status(),
                command.keyword(),
                command.sourceType(),
                command.sourceId(),
                requireText(command.embeddingProvider(), "embeddingProvider"),
                requireText(command.embeddingModel(), "embeddingModel"),
                command.embeddingDimension() <= 0 ? 768 : command.embeddingDimension(),
                targetTypes,
                command.topK() <= 0 ? 5 : Math.min(command.topK(), 20),
                threshold(command.minScore(), 0.75d),
                threshold(command.newSkillMinConfidence(), 0.80d),
                threshold(command.existingSkillMinScore(), 0.92d));
    }

    private SkillRecommendationApplyCommand normalizeApply(SkillRecommendationApplyCommand command) {
        List<String> types = command == null || command.recommendationTypes() == null || command.recommendationTypes().isEmpty()
                ? List.of(SkillRecommendationType.NEW_SKILL_CANDIDATE.name(), SkillRecommendationType.EXISTING_SKILL_MATCH.name())
                : command.recommendationTypes().stream().map(value -> value.toUpperCase(Locale.ROOT)).distinct().toList();
        return new SkillRecommendationApplyCommand(
                command == null || command.applyMode() == null ? "ELIGIBLE_ONLY" : command.applyMode(),
                types,
                command == null ? 0.80d : clamp(command.minConfidence()),
                command == null ? 0.92d : clamp(command.minSimilarityScore()));
    }

    private SkillRecommendationJob findJob(String jobId) {
        return recommendationStore.findJob(requireText(jobId, "jobId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown recommendation job: " + jobId));
    }

    private String filterJson(SkillCandidateRecommendationJobCommand command) {
        return "{\"status\":\"%s\",\"keyword\":\"%s\",\"sourceType\":\"%s\",\"sourceId\":\"%s\",\"candidateIds\":%d}"
                .formatted(nullToEmpty(command.status()), nullToEmpty(command.keyword()), nullToEmpty(command.sourceType()),
                        nullToEmpty(command.sourceId()), command.candidateIds().size());
    }

    private double threshold(double value, double defaultValue) {
        double candidate = value <= 0 ? defaultValue : value;
        return Math.max(0.0d, Math.min(1.0d, candidate));
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
