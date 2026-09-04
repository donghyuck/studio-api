package studio.one.platform.team.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import studio.one.platform.team.domain.model.TeamRole;

public record TeamMemberRequest(@NotNull @Positive Long userId, @NotNull TeamRole role) {
}
