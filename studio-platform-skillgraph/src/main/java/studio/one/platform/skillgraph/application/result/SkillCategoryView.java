package studio.one.platform.skillgraph.application.result;

import studio.one.platform.skillgraph.domain.model.SkillCategory;

public record SkillCategoryView(String categoryId, String parentCategoryId, String name, int displayOrder) {

    public static SkillCategoryView from(SkillCategory category) {
        return new SkillCategoryView(category.categoryId(), category.parentCategoryId(), category.name(), category.displayOrder());
    }
}
