package studio.one.platform.skillgraph.web.dto.response;

import java.time.Instant;

import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;

public record SkillProjectionSummaryDto(
        String projectionId,
        String name,
        String status,
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
        int itemCount,
        int clusterCount,
        Instant createdAt,
        Instant updatedAt) {

    public static SkillProjectionSummaryDto from(SkillProjectionSummaryView view) {
        return new SkillProjectionSummaryDto(
                view.projectionId(),
                view.projectionId(),
                "READY",
                view.algorithm(),
                view.skillType(),
                view.jobId(),
                view.projectionType(),
                view.reductionAlgorithm(),
                view.projectionDimension(),
                view.embeddingProvider(),
                view.embeddingModel(),
                view.embeddingDimension(),
                view.metadata(),
                view.itemCount(),
                view.clusterCount(),
                view.createdAt(),
                view.updatedAt());
    }
}
