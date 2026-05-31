package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillCandidateEmbeddingRequest(
        @NotBlank @Size(max = 100) String embeddingProvider,
        @NotBlank @Size(max = 200) String embeddingModel,
        @Min(1) @Max(4096) Integer embeddingDim,
        @Min(1) @Max(2000) Integer limit) {
}
