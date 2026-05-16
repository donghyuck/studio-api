package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillCluster(String clusterId, String label, String algorithm, int itemCount, Instant createdAt) {

    public SkillCluster {
        clusterId = requireText(clusterId, "clusterId");
        label = normalize(label);
        algorithm = requireText(algorithm, "algorithm");
        itemCount = Math.max(0, itemCount);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public SkillCluster(String clusterId, String label, String algorithm, int itemCount) {
        this(clusterId, label, algorithm, itemCount, null);
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
