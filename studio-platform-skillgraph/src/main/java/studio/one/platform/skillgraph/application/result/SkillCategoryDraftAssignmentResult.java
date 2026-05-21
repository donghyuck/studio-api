package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryDraftAssignmentResult(
        String projectionId,
        int savedCategoryCount,
        int assignedSkillCount,
        List<SkillCategoryDraftAssignmentItem> results) {

    public record SkillCategoryDraftAssignmentItem(
            String clusterId,
            String categoryId,
            String categoryName,
            boolean saved,
            int assignedCount) {
    }
}
