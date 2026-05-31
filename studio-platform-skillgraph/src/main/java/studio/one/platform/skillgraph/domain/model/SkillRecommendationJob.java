package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillRecommendationJob(
        String jobId,
        String targetScope,
        String targetFilter,
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
}
