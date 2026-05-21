package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillCategoryHistory;

public record SkillCategoryHistoryView(
        String historyId,
        String categoryId,
        String skillId,
        String actionType,
        String previousCategoryId,
        String newCategoryId,
        String detail,
        Instant createdAt) {

    public static SkillCategoryHistoryView from(SkillCategoryHistory history) {
        return new SkillCategoryHistoryView(
                history.historyId(),
                history.categoryId(),
                history.skillId(),
                history.actionType(),
                history.previousCategoryId(),
                history.newCategoryId(),
                history.detail(),
                history.createdAt());
    }
}
