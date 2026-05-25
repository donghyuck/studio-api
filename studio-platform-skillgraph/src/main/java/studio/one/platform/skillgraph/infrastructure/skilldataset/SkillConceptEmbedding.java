package studio.one.platform.skillgraph.infrastructure.skilldataset;

import java.util.List;

public record SkillConceptEmbedding(
        String embeddingId,
        String conceptId,
        String datasetId,
        String provider,
        String conceptType,
        String externalCode,
        String preferredLabel,
        String embeddingProvider,
        String embeddingModel,
        int embeddingDim,
        String textType,
        String sourceText,
        String sourceHash,
        List<Double> embedding
) {
}
