package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillRecommendationJob;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationJobStatus;

public record SkillRecommendationJobView(
        String jobId,
        String targetScope,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDimension,
        String targetTypes,
        int topK,
        double minScore,
        double newSkillMinConfidence,
        double existingSkillMinScore,
        SkillRecommendationJobStatus status,
        long totalCount,
        long processedCount,
        long resultCount,
        long failedCount,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {

    public static SkillRecommendationJobView from(SkillRecommendationJob job) {
        return new SkillRecommendationJobView(
                job.jobId(),
                job.targetScope(),
                job.embeddingProvider(),
                job.embeddingModel(),
                job.embeddingDimension(),
                job.targetTypes(),
                job.topK(),
                job.minScore(),
                job.newSkillMinConfidence(),
                job.existingSkillMinScore(),
                job.status(),
                job.totalCount(),
                job.processedCount(),
                job.resultCount(),
                job.failedCount(),
                job.errorMessage(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.updatedAt());
    }
}
