package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillReferenceEmbeddingRequest(
        @NotBlank @Size(max = 80) String datasetId,
        @Size(max = 30) String provider,
        @Size(max = 80) String conceptType,
        @NotBlank @Size(max = 50) String embeddingProvider,
        @NotBlank @Size(max = 150) String embeddingModel,
        @Min(0) @Max(10000) int embeddingDim,
        @NotBlank @Size(max = 50) String textType,
        @Size(max = 80) String textBuildStrategy,
        @Min(0) @Max(100) int batchSize,
        boolean overwrite,
        boolean normalize
) {
}
