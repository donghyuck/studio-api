package studio.one.platform.team.web.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TeamMigrationDryRunRequest(
        @NotBlank String idempotencyKey,
        @NotNull @Positive Long targetTeamId,
        @NotEmpty List<@NotNull @Positive Long> sourceRootWorkspaceIds) {
}
