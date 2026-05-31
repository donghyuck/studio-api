package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillRecommendationResult(
        String resultId,
        String jobId,
        String sourceType,
        String sourceId,
        String sourceText,
        String targetSourceType,
        String targetSourceId,
        String targetText,
        SkillRecommendationType recommendationType,
        double similarityScore,
        double confidence,
        String scoreDetail,
        String reason,
        SkillRecommendationResultStatus status,
        String applyType,
        Instant appliedAt,
        String appliedBy,
        Instant createdAt) {

    public boolean bulkApplicable() {
        return recommendationType == SkillRecommendationType.NEW_SKILL_CANDIDATE
                || recommendationType == SkillRecommendationType.EXISTING_SKILL_MATCH;
    }
}
