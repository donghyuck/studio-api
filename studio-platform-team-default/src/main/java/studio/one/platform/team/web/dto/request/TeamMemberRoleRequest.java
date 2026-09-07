package studio.one.platform.team.web.dto.request;

import jakarta.validation.constraints.NotNull;
import studio.one.platform.team.domain.model.TeamRole;

public record TeamMemberRoleRequest(@NotNull TeamRole role) {
}
