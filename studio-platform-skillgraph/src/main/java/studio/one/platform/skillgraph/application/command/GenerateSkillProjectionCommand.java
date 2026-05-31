package studio.one.platform.skillgraph.application.command;

public record GenerateSkillProjectionCommand(
        String projectionId,
        int limit,
        String reductionAlgorithm,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {
}
