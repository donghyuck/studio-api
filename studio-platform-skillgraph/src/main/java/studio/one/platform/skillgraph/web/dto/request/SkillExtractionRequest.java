package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillExtractionRequest(
        @Size(max = 100)
        @NotBlank String sourceType,
        @Size(max = 200)
        @NotBlank String sourceId,
        @Size(max = 200)
        String chunkId,
        @Size(max = 200000)
        @NotBlank String text) {
}
