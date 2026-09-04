package studio.one.platform.team.application.command;

import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamVisibility;

public record UpdateTeamCommand(
        Long companyId,
        boolean companyIdSpecified,
        String name,
        String description,
        TeamVisibility visibility,
        TeamJoinPolicy joinPolicy,
        Boolean ragEnabled,
        TeamRagReplyMode ragReplyMode,
        TeamAccessContext actor) {
}
