package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public record UpdateWorkspaceCommand(
        String name,
        WorkspaceVisibility visibility,
        WorkspaceAccessContext actor) {
}
