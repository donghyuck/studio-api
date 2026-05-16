package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record SkillProjectionRequest(
        @Size(max = 100) String projectionId,
        @Min(1) @Max(2000) Integer limit) {
}
