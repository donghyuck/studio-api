package studio.one.platform.skillgraph.application.command;

public record NcsSkillMappingCommand(String mappingId, String ncsUnitId, String skillId, double weight) {
}
