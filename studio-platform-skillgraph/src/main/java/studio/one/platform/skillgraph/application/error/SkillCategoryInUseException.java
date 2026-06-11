package studio.one.platform.skillgraph.application.error;

import studio.one.platform.skillgraph.application.result.SkillCategoryDeletionImpact;

public class SkillCategoryInUseException extends RuntimeException {

    private final SkillCategoryDeletionImpact impact;

    public SkillCategoryInUseException(SkillCategoryDeletionImpact impact) {
        super("CATEGORY_IN_USE: categoryId=%s, skillCount=%d, childCount=%d"
                .formatted(impact.categoryId(), impact.skillCount(), impact.childCount()));
        this.impact = impact;
    }

    public SkillCategoryDeletionImpact impact() {
        return impact;
    }
}
