package studio.one.platform.skillgraph.application.command;

public record SkillReferenceEmbeddingCommand(
        String datasetId,
        String provider,
        String conceptType,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDim,
        String textType,
        String textBuildStrategy,
        int batchSize,
        boolean overwrite,
        boolean normalize
) {
}
