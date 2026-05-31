package studio.one.platform.skillgraph.application.command;

import java.util.List;

public record SkillRecommendationApplyCommand(
        String applyMode,
        List<String> recommendationTypes,
        double minConfidence,
        double minSimilarityScore) {
}
