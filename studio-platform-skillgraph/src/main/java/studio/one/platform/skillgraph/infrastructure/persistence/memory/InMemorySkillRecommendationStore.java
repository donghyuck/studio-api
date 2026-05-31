package studio.one.platform.skillgraph.infrastructure.persistence.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.domain.model.SkillRecommendationJob;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResult;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResultStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationTargetHit;
import studio.one.platform.skillgraph.domain.port.SkillRecommendationStore;

public class InMemorySkillRecommendationStore implements SkillRecommendationStore {

    private final Map<String, SkillRecommendationJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, SkillRecommendationResult> results = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingRow> embeddings = new ConcurrentHashMap<>();

    @Override
    public SkillRecommendationJob saveJob(SkillRecommendationJob job) {
        jobs.put(job.jobId(), job);
        return job;
    }

    @Override
    public Optional<SkillRecommendationJob> findJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Page<SkillRecommendationJob> searchJobs(Pageable pageable) {
        List<SkillRecommendationJob> sorted = jobs.values().stream()
                .sorted(Comparator.comparing(SkillRecommendationJob::createdAt).reversed())
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    @Override
    public SkillRecommendationResult saveResult(SkillRecommendationResult result) {
        results.put(result.resultId(), result);
        return result;
    }

    @Override
    public Optional<SkillRecommendationResult> findResult(String resultId) {
        return Optional.ofNullable(results.get(resultId));
    }

    @Override
    public Page<SkillRecommendationResult> findResultsByJob(String jobId, Pageable pageable) {
        List<SkillRecommendationResult> sorted = results.values().stream()
                .filter(result -> jobId.equals(result.jobId()))
                .sorted(Comparator.comparing(SkillRecommendationResult::createdAt))
                .toList();
        int start = Math.toIntExact(Math.min(pageable.getOffset(), sorted.size()));
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    @Override
    public List<SkillRecommendationResult> findResultsByCandidate(String candidateId) {
        return results.values().stream()
                .filter(result -> candidateId.equals(result.sourceId()))
                .sorted(Comparator.comparing(SkillRecommendationResult::createdAt).reversed())
                .toList();
    }

    @Override
    public int updateResultStatus(
            String resultId,
            SkillRecommendationResultStatus status,
            String applyType,
            String appliedBy,
            String reason) {
        SkillRecommendationResult existing = results.get(resultId);
        if (existing == null) {
            return 0;
        }
        results.put(resultId, new SkillRecommendationResult(
                existing.resultId(), existing.jobId(), existing.sourceType(), existing.sourceId(), existing.sourceText(),
                existing.targetSourceType(), existing.targetSourceId(), existing.targetText(), existing.recommendationType(),
                existing.similarityScore(), existing.confidence(), existing.scoreDetail(),
                reason == null ? existing.reason() : reason, status, applyType,
                status == SkillRecommendationResultStatus.APPLIED ? java.time.Instant.now() : existing.appliedAt(),
                appliedBy == null ? existing.appliedBy() : appliedBy, existing.createdAt()));
        return 1;
    }

    @Override
    public List<Double> findEmbedding(String sourceType, String sourceId, String provider, String model, int dimension) {
        EmbeddingRow row = embeddings.get(key(sourceType, sourceId, provider, model));
        if (row == null || row.dimension != dimension) {
            return List.of();
        }
        return row.embedding;
    }

    @Override
    public List<SkillRecommendationTargetHit> findNearestEmbeddings(
            String sourceType,
            String provider,
            String model,
            int dimension,
            List<Double> embedding,
            String excludedSourceId,
            int limit,
            double minScore) {
        return embeddings.values().stream()
                .filter(row -> sourceType.equals(row.sourceType))
                .filter(row -> provider.equals(row.provider) && model.equals(row.model) && dimension == row.dimension)
                .filter(row -> excludedSourceId == null || !excludedSourceId.equals(row.sourceId))
                .map(row -> new SkillRecommendationTargetHit(row.sourceType, row.sourceId, row.text, cosine(embedding, row.embedding)))
                .filter(hit -> hit.score() >= minScore)
                .sorted(Comparator.comparing(SkillRecommendationTargetHit::score).reversed())
                .limit(limit <= 0 ? 5 : limit)
                .toList();
    }

    public void saveEmbedding(String sourceType, String sourceId, String provider, String model, int dimension, String text, List<Double> embedding) {
        embeddings.put(key(sourceType, sourceId, provider, model),
                new EmbeddingRow(sourceType, sourceId, provider, model, dimension, text, new ArrayList<>(embedding)));
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || left.size() != right.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String key(String sourceType, String sourceId, String provider, String model) {
        return sourceType + "|" + sourceId + "|" + provider + "|" + model;
    }

    private record EmbeddingRow(
            String sourceType,
            String sourceId,
            String provider,
            String model,
            int dimension,
            String text,
            List<Double> embedding) {
    }
}
