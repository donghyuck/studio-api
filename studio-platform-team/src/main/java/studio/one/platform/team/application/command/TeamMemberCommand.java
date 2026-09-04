package studio.one.platform.team.application.command;

import studio.one.platform.team.domain.model.TeamRole;

public record TeamMemberCommand(
        Long userId,
        TeamRole role,
        TeamAccessContext actor) {
}
