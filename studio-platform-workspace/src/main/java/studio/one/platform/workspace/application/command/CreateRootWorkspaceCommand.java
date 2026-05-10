package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public record CreateRootWorkspaceCommand(
        Long companyId,
        String name,
        String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessContext actor) {

    public static CreateRootWorkspaceCommand from(CreateWorkspaceCommand command) {
        return new CreateRootWorkspaceCommand(
                null,
                command.name(),
                command.slug(),
                command.visibility(),
                command.actor());
    }
}
