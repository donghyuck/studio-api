package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;
import java.util.List;

public record SkillVectorItem(
        String skillId,
        String label,
        List<Double> embedding,
        String embeddingModel,
        Instant createdAt) {

    public SkillVectorItem {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        skillId = skillId.trim();
        label = label == null || label.isBlank() ? skillId : label.trim();
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
