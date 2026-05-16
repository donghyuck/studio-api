package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillProjection(
        String projectionId,
        String skillId,
        double x,
        double y,
        String clusterId,
        int displayOrder,
        Instant createdAt) {

    public SkillProjection {
        projectionId = requireText(projectionId, "projectionId");
        skillId = requireText(skillId, "skillId");
        clusterId = normalize(clusterId);
        displayOrder = Math.max(0, displayOrder);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public SkillProjection(String projectionId, String skillId, double x, double y, String clusterId) {
        this(projectionId, skillId, x, y, clusterId, 0, null);
    }

    public SkillProjection withClusterId(String nextClusterId) {
        return new SkillProjection(projectionId, skillId, x, y, nextClusterId, displayOrder, createdAt);
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
