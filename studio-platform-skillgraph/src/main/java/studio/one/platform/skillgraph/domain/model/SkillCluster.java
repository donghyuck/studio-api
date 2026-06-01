package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;
import java.util.List;

public record SkillCluster(
        String clusterId,
        String label,
        String algorithm,
        int itemCount,
        String skillType,
        String jobId,
        Integer clusterLabel,
        List<String> representativeSkillIds,
        String centroidProjectionId,
        Double confidence,
        String metadata,
        Instant createdAt) {

    public SkillCluster(String clusterId, String label, String algorithm, int itemCount, Instant createdAt) {
        this(clusterId, label, algorithm, itemCount, null, null, null, List.of(), null, null, null, createdAt);
    }

    public SkillCluster {
        clusterId = requireText(clusterId, "clusterId");
        label = normalize(label);
        algorithm = requireText(algorithm, "algorithm");
        itemCount = Math.max(0, itemCount);
        skillType = SkillType.normalizeNameOrNull(skillType);
        jobId = normalize(jobId);
        representativeSkillIds = representativeSkillIds == null ? List.of() : representativeSkillIds.stream()
                .map(SkillCluster::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
        centroidProjectionId = normalize(centroidProjectionId);
        if (confidence != null) {
            confidence = Math.max(0.0d, Math.min(1.0d, confidence));
        }
        metadata = normalize(metadata);
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
