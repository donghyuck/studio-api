package studio.one.platform.workspace.application.service;

import lombok.RequiredArgsConstructor;
import studio.one.platform.team.application.command.TeamAccessContext;
import studio.one.platform.team.application.usecase.TeamWorkspaceProvisioningPort;
import studio.one.platform.team.domain.model.TeamRef;
import studio.one.platform.workspace.application.command.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;
import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

/** Creates the initial root Workspace owned by a Team. */
@RequiredArgsConstructor
public class DefaultTeamWorkspaceProvisioningAdapter implements TeamWorkspaceProvisioningPort {

    private final WorkspaceTreeService workspaceTreeService;

    @Override
    public Long createRootWorkspace(TeamRef team, TeamAccessContext actor) {
        return workspaceTreeService.createRoot(new CreateRootWorkspaceCommand(
                team.companyId(),
                team.teamId(),
                team.name(),
                team.slug(),
                WorkspaceVisibility.PRIVATE,
                WorkspaceAccessMode.INHERIT,
                new WorkspaceAccessContext(actor.requireUserId(), actor.username(), actor.platformAdmin())))
                .id();
    }
}
