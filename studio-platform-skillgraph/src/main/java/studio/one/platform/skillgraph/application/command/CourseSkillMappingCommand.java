package studio.one.platform.skillgraph.application.command;

public record CourseSkillMappingCommand(String mappingId, String courseId, String skillId, double weight) {
}
