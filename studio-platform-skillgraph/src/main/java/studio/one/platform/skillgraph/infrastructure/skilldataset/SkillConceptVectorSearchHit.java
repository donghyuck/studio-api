package studio.one.platform.skillgraph.infrastructure.skilldataset;

public record SkillConceptVectorSearchHit(
        SkillConcept concept,
        String embeddingId,
        String embeddingProvider,
        String embeddingModel,
        String textType,
        double score,
        String sourceText
) {
}
