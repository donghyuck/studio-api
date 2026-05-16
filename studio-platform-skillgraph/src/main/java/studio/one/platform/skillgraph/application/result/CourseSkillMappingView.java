package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.CourseSkillMapping;

public record CourseSkillMappingView(String mappingId, String courseId, String skillId, double weight, Instant createdAt) {
    public static CourseSkillMappingView from(CourseSkillMapping mapping) {
        return new CourseSkillMappingView(mapping.mappingId(), mapping.courseId(), mapping.skillId(), mapping.weight(), mapping.createdAt());
    }
}
