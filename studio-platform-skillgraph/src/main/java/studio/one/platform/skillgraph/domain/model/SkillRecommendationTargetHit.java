package studio.one.platform.skillgraph.domain.model;

public record SkillRecommendationTargetHit(
        String sourceType,
        String sourceId,
        String sourceText,
        double score) {
}
