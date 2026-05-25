package studio.one.platform.skillgraph.application.result;

public record SkillReferenceVectorSearchResult(
        SkillReferenceConceptView concept,
        String embeddingId,
        String embeddingProvider,
        String embeddingModel,
        String textType,
        double score,
        String sourceText
) {
}
