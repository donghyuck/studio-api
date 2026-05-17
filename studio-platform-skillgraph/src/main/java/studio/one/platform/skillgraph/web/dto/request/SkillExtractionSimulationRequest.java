package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillExtractionSimulationRequest(
        @Size(max = 100)
        String sourceType,
        @Size(max = 200)
        String sourceId,
        @Size(max = 200)
        String chunkId,
        @Size(max = 200000)
        @NotBlank String text) {
}
