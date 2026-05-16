package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillAlias(String aliasId, String skillId, String alias, String normalizedAlias, Instant createdAt) {

    public SkillAlias {
        aliasId = requireText(aliasId, "aliasId");
        skillId = requireText(skillId, "skillId");
        alias = requireText(alias, "alias");
        normalizedAlias = SkillCandidate.normalizeSkillTerm(normalizedAlias == null ? alias : normalizedAlias);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public SkillAlias(String aliasId, String skillId, String alias, String normalizedAlias) {
        this(aliasId, skillId, alias, normalizedAlias, null);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
