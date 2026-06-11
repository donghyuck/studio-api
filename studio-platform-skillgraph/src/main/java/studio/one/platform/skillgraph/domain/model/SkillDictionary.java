package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillDictionary(
        String skillId,
        String name,
        String normalizedName,
        String categoryId,
        String skillType,
        String status,
        boolean embedded,
        Instant createdAt,
        Instant updatedAt) {

    public SkillDictionary(
            String skillId,
            String name,
            String normalizedName,
            String categoryId,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        this(skillId, name, normalizedName, categoryId, null, status, false, createdAt, updatedAt);
    }

    public SkillDictionary(
            String skillId,
            String name,
            String normalizedName,
            String categoryId,
            String skillType,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        this(skillId, name, normalizedName, categoryId, skillType, status, false, createdAt, updatedAt);
    }

    public SkillDictionary {
        skillId = requireText(skillId, "skillId");
        name = requireText(name, "name");
        normalizedName = SkillCandidate.normalizeSkillTerm(normalizedName == null ? name : normalizedName);
        categoryId = normalize(categoryId);
        skillType = SkillType.normalizeName(skillType);
        status = normalize(status) == null ? "ACTIVE" : status.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
