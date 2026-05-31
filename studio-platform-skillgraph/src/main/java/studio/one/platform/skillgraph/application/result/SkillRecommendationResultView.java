package studio.one.platform.skillgraph.application.result;

import java.time.Instant;

import studio.one.platform.skillgraph.domain.model.SkillRecommendationResult;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationResultStatus;
import studio.one.platform.skillgraph.domain.model.SkillRecommendationType;

public record SkillRecommendationResultView(
        String resultId,
        String jobId,
        String sourceId,
        String sourceText,
        String targetSourceType,
        String targetSourceId,
        String targetText,
        SkillRecommendationType recommendationType,
        double similarityScore,
        double confidence,
        String reason,
        SkillRecommendationResultStatus status,
        String applyType,
        boolean bulkApplicable,
        Instant appliedAt,
        String appliedBy,
        Instant createdAt) {

    public static SkillRecommendationResultView from(SkillRecommendationResult result) {
        return new SkillRecommendationResultView(
                result.resultId(),
                result.jobId(),
                result.sourceId(),
                result.sourceText(),
                result.targetSourceType(),
                result.targetSourceId(),
                result.targetText(),
                result.recommendationType(),
                result.similarityScore(),
                result.confidence(),
                result.reason(),
                result.status(),
                result.applyType(),
                result.bulkApplicable(),
                result.appliedAt(),
                result.appliedBy(),
                result.createdAt());
    }
}
