package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillReferenceVectorSearchRequest(
        @NotBlank @Size(max = 80) String datasetId,
        @Size(max = 30) String provider,
        @Size(max = 80) String conceptType,
        @NotBlank @Size(max = 50) String embeddingProvider,
        @NotBlank @Size(max = 150) String embeddingModel,
        @NotBlank @Size(max = 50) String textType,
        @NotBlank @Size(max = 1000) String query,
        @Min(0) @Max(100) int topK,
        @Min(0) @Max(1) double minScore,
        @Size(max = 2000) String categoryPathPrefix,
        @Size(max = 20) String levelValue,
        boolean normalize
) {
}
