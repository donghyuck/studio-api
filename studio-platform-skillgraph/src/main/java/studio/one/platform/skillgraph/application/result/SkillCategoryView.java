package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.domain.model.SkillCategory;

public record SkillCategoryView(
        String categoryId,
        String parentCategoryId,
        String name,
        int displayOrder,
        int skillCount) {

    public static SkillCategoryView from(SkillCategory category) {
        return from(category, 0);
    }

    public static SkillCategoryView from(SkillCategory category, int skillCount) {
        return new SkillCategoryView(
                category.categoryId(),
                category.parentCategoryId(),
                category.name(),
                category.displayOrder(),
                Math.max(0, skillCount));
    }
}
