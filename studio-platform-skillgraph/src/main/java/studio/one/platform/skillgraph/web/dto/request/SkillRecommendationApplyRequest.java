package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SkillRecommendationApplyRequest(
        @Size(max = 50) String applyMode,
        List<@Size(max = 80) String> recommendationTypes,
        @Min(0) @Max(1) double minConfidence,
        @Min(0) @Max(1) double minSimilarityScore) {
}
