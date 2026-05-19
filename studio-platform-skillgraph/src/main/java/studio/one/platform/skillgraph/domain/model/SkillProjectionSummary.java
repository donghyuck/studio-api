package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillProjectionSummary(
        String projectionId,
        int itemCount,
        int clusterCount,
        String algorithm,
        Instant createdAt,
        Instant updatedAt) {

    public SkillProjectionSummary {
        projectionId = requireText(projectionId, "projectionId");
        itemCount = Math.max(0, itemCount);
        clusterCount = Math.max(0, clusterCount);
        algorithm = normalize(algorithm);
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
