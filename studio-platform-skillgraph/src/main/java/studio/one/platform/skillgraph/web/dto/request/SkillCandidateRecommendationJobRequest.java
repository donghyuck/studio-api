package studio.one.platform.skillgraph.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillCandidateRecommendationJobRequest(
        @Size(max = 50) String targetScope,
        List<@Size(max = 100) String> candidateIds,
        @Size(max = 40) String status,
        @Size(max = 200) String keyword,
        @Size(max = 100) String sourceType,
        @Size(max = 200) String sourceId,
        @NotBlank @Size(max = 100) String embeddingProvider,
        @NotBlank @Size(max = 200) String embeddingModel,
        @Min(1) @Max(4096) int embeddingDimension,
        List<@Size(max = 80) String> targetTypes,
        @Min(1) @Max(20) int topK,
        @Min(0) @Max(1) double minScore,
        @Min(0) @Max(1) double newSkillMinConfidence,
        @Min(0) @Max(1) double existingSkillMinScore) {
}
