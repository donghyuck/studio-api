package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillDatasetImportRequest(
        @NotBlank @Size(max = 30) String provider,
        @NotBlank @Size(max = 80) String datasetId,
        @Size(max = 200) String datasetName,
        @Size(max = 50) String version,
        @Size(max = 10) String language,
        @NotBlank @Size(max = 1000) String sourceLocation
) {

}
