package studio.one.platform.skillgraph.application.result;

import java.util.List;

public record SkillRecommendationApplyResult(
        int requestedCount,
        int appliedCount,
        int skippedCount,
        int failedCount,
        List<SkillRecommendationApplySkip> skipped,
        List<SkillRecommendationApplySkip> failed) {
}
