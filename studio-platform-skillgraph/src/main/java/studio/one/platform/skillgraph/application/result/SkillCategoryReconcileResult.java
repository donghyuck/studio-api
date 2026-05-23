package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillCategoryReconcileResult(
        int scannedCount,
        int matchedExistingCount,
        int newCategoryDraftCount,
        int noiseCount,
        List<ExistingCategoryAssignmentCandidate> matchedExisting,
        List<SkillCategoryDraftView> newCategoryDrafts,
        List<String> noiseSkillIds) {

    public record ExistingCategoryAssignmentCandidate(
            String skillId,
            String skillName,
            String categoryId,
            String categoryName,
            double similarity) {
    }
}
