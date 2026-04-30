package studio.one.platform.ai.web.dto.visualization;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VectorSearchVisualizationRequest(
        @NotBlank String projectionId,
        @NotBlank @Size(max = 2000) String query,
        List<String> targetTypes,
        Integer topK,
        Double minScore) {
}
