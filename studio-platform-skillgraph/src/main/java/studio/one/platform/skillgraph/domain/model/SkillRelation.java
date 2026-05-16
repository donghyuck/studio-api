package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillRelation(
        String relationId,
        String sourceSkillId,
        String targetSkillId,
        SkillRelationType type,
        Instant createdAt) {

    public SkillRelation {
        relationId = requireText(relationId, "relationId");
        sourceSkillId = requireText(sourceSkillId, "sourceSkillId");
        targetSkillId = requireText(targetSkillId, "targetSkillId");
        type = type == null ? SkillRelationType.RELATED : type;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public SkillRelation(String relationId, String sourceSkillId, String targetSkillId, SkillRelationType type) {
        this(relationId, sourceSkillId, targetSkillId, type, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
