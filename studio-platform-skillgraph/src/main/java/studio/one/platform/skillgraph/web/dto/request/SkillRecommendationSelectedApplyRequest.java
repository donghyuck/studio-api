package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SkillRecommendationSelectedApplyRequest(
        @NotEmpty @Size(max = 200) List<@Size(min = 1, max = 120) String> resultIds,
        @Size(max = 50) String applyMode,
        List<@Size(max = 80) String> recommendationTypes,
        @Min(0) @Max(1) double minConfidence,
        @Min(0) @Max(1) double minSimilarityScore) {
}
