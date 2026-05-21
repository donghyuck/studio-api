package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillCategoryHistory(
        String historyId,
        String categoryId,
        String skillId,
        String actionType,
        String previousCategoryId,
        String newCategoryId,
        String detail,
        Instant createdAt) {
}
