package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillProjectionResult(
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
        List<SkillProjectionPointView> points,
        List<SkillClusterView> clusters) {

    public SkillProjectionResult(
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
        this(projectionId, itemCount, clusterCount, null, null, reductionAlgorithm, null, clusteringAlgorithm,
                embeddingProvider, embeddingModel, embeddingDimension, null, points, clusters);
    }
}
