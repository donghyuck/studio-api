package studio.one.platform.skillgraph.application.command;

public record SkillReferenceVectorSearchCommand(
        String datasetId,
        String provider,
        String conceptType,
        String embeddingProvider,
        String embeddingModel,
        String textType,
        String query,
        int topK,
        double minScore,
        String categoryPathPrefix,
        String levelValue,
        boolean normalize
) {
}
