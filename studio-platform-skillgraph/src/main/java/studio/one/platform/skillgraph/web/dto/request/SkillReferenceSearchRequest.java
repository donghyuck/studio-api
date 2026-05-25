package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillReferenceSearchRequest(
        @Size(max = 80) String datasetId,
        @Size(max = 80) String conceptType,
        @NotBlank @Size(max = 200) String query) {
}
