package studio.one.platform.skillgraph.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignCategoryFromClusterCommand(
        @NotBlank @Size(max = 100) String projectionId,
        @NotBlank @Size(max = 100) String clusterId,
        Boolean includeNoise) {
}
