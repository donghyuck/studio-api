package studio.one.platform.skillgraph.application.result;

public record SkillCategoryDeletionImpact(
        String categoryId,
        int skillCount,
        int childCount,
        boolean deletable) {
}
