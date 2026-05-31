package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillProjectionSummary;

public record SkillProjectionSummaryView(
        String projectionId,
        int itemCount,
        int clusterCount,
        String algorithm,
        String reductionAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillProjectionSummaryView from(SkillProjectionSummary summary) {
        return new SkillProjectionSummaryView(
                summary.projectionId(),
                summary.itemCount(),
                summary.clusterCount(),
                summary.algorithm(),
                summary.reductionAlgorithm(),
                summary.embeddingProvider(),
                summary.embeddingModel(),
                summary.embeddingDimension(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
