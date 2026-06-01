package studio.one.platform.skillgraph.web.dto.response;

import java.util.List;

import studio.one.platform.skillgraph.application.result.SkillProjectionResult;

public record SkillProjectionResponse(
        String projectionId,
        int itemCount,
        int clusterCount,
        String skillType,
        String projectionType,
        String reductionAlgorithm,
        Integer projectionDimension,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String metadata,
        List<SkillProjectionPointDto> points,
        List<SkillClusterDto> clusters) {

    public static SkillProjectionResponse from(SkillProjectionResult result) {
        return new SkillProjectionResponse(result.projectionId(), result.itemCount(), result.clusterCount(),
                result.skillType(),
                result.projectionType(),
                result.reductionAlgorithm(),
                result.projectionDimension(),
                result.clusteringAlgorithm(),
                result.embeddingProvider(),
                result.embeddingModel(),
                result.embeddingDimension(),
                result.metadata(),
                result.points().stream().map(SkillProjectionPointDto::from).toList(),
                result.clusters().stream().map(SkillClusterDto::from).toList());
    }
}
