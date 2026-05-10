package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public record CreateWorkspaceCommand(
        String name,
        String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessContext actor) {
}
