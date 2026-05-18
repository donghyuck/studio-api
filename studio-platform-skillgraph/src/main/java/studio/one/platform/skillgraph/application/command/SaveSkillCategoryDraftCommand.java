package studio.one.platform.skillgraph.application.command;

import java.util.List;

public record SaveSkillCategoryDraftCommand(
        List<SaveSkillCategoryItem> categories) {

    public record SaveSkillCategoryItem(
            String categoryId,
            String parentCategoryId,
            String name,
            Integer displayOrder) {
    }
}
