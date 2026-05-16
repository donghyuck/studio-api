package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record CourseSkillMapping(String mappingId, String courseId, String skillId, double weight, Instant createdAt) {

    public CourseSkillMapping {
        mappingId = requireText(mappingId, "mappingId");
        courseId = requireText(courseId, "courseId");
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
