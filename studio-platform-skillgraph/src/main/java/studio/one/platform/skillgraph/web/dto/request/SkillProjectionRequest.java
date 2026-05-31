package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SkillProjectionRequest(
        @Size(max = 100) String projectionId,
        @Min(1) Integer limit,
        @Size(max = 30) String reductionAlgorithm,
        @Size(max = 30) String clusteringAlgorithm,
        @Size(max = 100) String embeddingProvider,
        @Size(max = 200) String embeddingModel,
        @Min(1) Integer embeddingDimension) {
}
