package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;
import java.util.List;

public record SkillVectorItem(
        String skillId,
        String label,
        String skillType,
        List<Double> embedding,
        String embeddingModel,
        Instant createdAt) {

    public SkillVectorItem(String skillId, String label, List<Double> embedding, String embeddingModel, Instant createdAt) {
        this(skillId, label, null, embedding, embeddingModel, createdAt);
    }

    public SkillVectorItem {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        skillId = skillId.trim();
        label = label == null || label.isBlank() ? skillId : label.trim();
        skillType = SkillType.normalizeName(skillType);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
