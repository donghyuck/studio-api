package studio.one.platform.team.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TeamMigrationApplyRequest(@NotBlank String runId) {
}
