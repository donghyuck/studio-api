package studio.one.platform.skillgraph.domain.model;

public record SkillProjectionMetadata(
        String jobId,
        String skillType,
        String projectionType,
        String reductionAlgorithm,
        Integer projectionDimension,
        String clusteringAlgorithm,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String parameters) {

    public SkillProjectionMetadata(
            String reductionAlgorithm,
            String clusteringAlgorithm,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension) {
        this(null, null, null, reductionAlgorithm, null, clusteringAlgorithm, embeddingProvider, embeddingModel,
                embeddingDimension, null);
    }

    public SkillProjectionMetadata {
        jobId = normalize(jobId);
        skillType = SkillType.normalizeNameOrNull(skillType);
        projectionType = normalize(projectionType);
        reductionAlgorithm = normalize(reductionAlgorithm);
        projectionDimension = projectionDimension == null || projectionDimension <= 0 ? null : projectionDimension;
        clusteringAlgorithm = normalize(clusteringAlgorithm);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        parameters = normalize(parameters);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
