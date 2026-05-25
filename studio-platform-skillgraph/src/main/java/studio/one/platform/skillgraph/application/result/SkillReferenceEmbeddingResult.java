package studio.one.platform.skillgraph.application.result;

public record SkillReferenceEmbeddingResult(
        String datasetId,
        String conceptType,
        String embeddingProvider,
        String embeddingModel,
        String textType,
        long totalCount,
        long processedCount,
        long embeddedCount,
        long skippedCount,
        long failedCount
) {
}
