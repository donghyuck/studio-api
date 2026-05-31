package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillProjectionResult(
        String projectionId,
        int itemCount,
        int clusterCount,
        String reductionAlgorithm,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        List<SkillProjectionPointView> points,
        List<SkillClusterView> clusters) {
}
