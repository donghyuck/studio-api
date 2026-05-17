package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRagDocumentExtractionRequest(
        @Size(max = 100)
        @NotBlank String objectType,
        @Size(max = 200)
        @NotBlank String objectId,
        @Size(max = 200)
        String documentId,
        String mode,
        @Min(1)
        @Max(5000)
        Integer limit) {
}
