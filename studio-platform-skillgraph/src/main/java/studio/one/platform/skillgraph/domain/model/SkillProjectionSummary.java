package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillProjectionSummary(
        String projectionId,
        int itemCount,
        int clusterCount,
        String algorithm,
        String skillType,
        String jobId,
        String projectionType,
        String reductionAlgorithm,
        Integer projectionDimension,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimension,
        String metadata,
        Instant createdAt,
        Instant updatedAt) {

    public SkillProjectionSummary(
            String projectionId,
            int itemCount,
            int clusterCount,
            String algorithm,
            String reductionAlgorithm,
            String embeddingProvider,
            String embeddingModel,
            Integer embeddingDimension,
            Instant createdAt,
            Instant updatedAt) {
        this(projectionId, itemCount, clusterCount, algorithm, null, null, null, reductionAlgorithm, null,
                embeddingProvider, embeddingModel, embeddingDimension, null, createdAt, updatedAt);
    }

    public SkillProjectionSummary {
        projectionId = requireText(projectionId, "projectionId");
        itemCount = Math.max(0, itemCount);
        clusterCount = Math.max(0, clusterCount);
        algorithm = normalize(algorithm);
        skillType = SkillType.normalizeName(skillType);
        jobId = normalize(jobId);
        projectionType = normalize(projectionType);
        reductionAlgorithm = normalize(reductionAlgorithm);
        projectionDimension = projectionDimension == null || projectionDimension <= 0 ? null : projectionDimension;
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        metadata = normalize(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
