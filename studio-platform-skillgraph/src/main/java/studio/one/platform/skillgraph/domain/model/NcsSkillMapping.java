package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record NcsSkillMapping(String mappingId, String ncsUnitId, String skillId, double weight, Instant createdAt) {

    public NcsSkillMapping {
        mappingId = requireText(mappingId, "mappingId");
        ncsUnitId = requireText(ncsUnitId, "ncsUnitId");
        skillId = requireText(skillId, "skillId");
        weight = Math.max(0.0d, Math.min(1.0d, weight));
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
