package studio.one.platform.skillgraph.application.command;

public record CreateSkillDictionaryCommand(
        String name,
        String normalizedName,
        String categoryId,
        String status,
        String description) {
}
