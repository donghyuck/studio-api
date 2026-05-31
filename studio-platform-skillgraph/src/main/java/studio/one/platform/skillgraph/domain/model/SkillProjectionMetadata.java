package studio.one.platform.skillgraph.domain.model;

public record SkillProjectionMetadata(
        String reductionAlgorithm,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension) {

    public SkillProjectionMetadata {
        reductionAlgorithm = normalize(reductionAlgorithm);
        clusteringAlgorithm = normalize(clusteringAlgorithm);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
