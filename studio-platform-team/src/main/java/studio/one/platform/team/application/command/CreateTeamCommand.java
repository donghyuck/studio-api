package studio.one.platform.team.application.command;

import studio.one.platform.team.domain.model.TeamJoinPolicy;
import studio.one.platform.team.domain.model.TeamRagReplyMode;
import studio.one.platform.team.domain.model.TeamVisibility;

public record CreateTeamCommand(
        Long companyId,
        String name,
        String slug,
        String description,
        TeamVisibility visibility,
        TeamJoinPolicy joinPolicy,
        Boolean ragEnabled,
        TeamRagReplyMode ragReplyMode,
        Boolean provisionRootWorkspace,
        TeamAccessContext actor) {

    public CreateTeamCommand(
            Long companyId,
            String name,
            String slug,
            String description,
            TeamVisibility visibility,
            TeamJoinPolicy joinPolicy,
            Boolean ragEnabled,
            TeamRagReplyMode ragReplyMode,
            TeamAccessContext actor) {
        this(companyId, name, slug, description, visibility, joinPolicy,
                ragEnabled, ragReplyMode, true, actor);
    }
}
