package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;

public record SkillProjectionSummaryView(
        String projectionId,
        int itemCount,
        int clusterCount,
        String algorithm,
        String skillType,
        String jobId,
        String projectionType,
        String reductionAlgorithm,
        Integer projectionDimension,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillProjectionSummaryView from(SkillProjectionSummary summary) {
        return new SkillProjectionSummaryView(
                summary.projectionId(),
                summary.itemCount(),
                summary.clusterCount(),
                summary.algorithm(),
                summary.skillType(),
                summary.jobId(),
                summary.projectionType(),
                summary.reductionAlgorithm(),
                summary.projectionDimension(),
                summary.embeddingProvider(),
                summary.embeddingModel(),
                summary.embeddingDimension(),
                summary.metadata(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
