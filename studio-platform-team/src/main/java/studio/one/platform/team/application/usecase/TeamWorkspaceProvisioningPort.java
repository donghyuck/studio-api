package studio.one.platform.team.application.usecase;

import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.domain.model.TeamRef;

@FunctionalInterface
public interface TeamWorkspaceProvisioningPort {
    Long createRootWorkspace(TeamRef team, TeamAccessContext actor);
}
