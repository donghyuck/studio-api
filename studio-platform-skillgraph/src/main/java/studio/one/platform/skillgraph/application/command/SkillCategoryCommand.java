package studio.one.platform.skillgraph.application.command;

public record SkillCategoryCommand(String categoryId, String parentCategoryId, String name, int displayOrder) {
}
