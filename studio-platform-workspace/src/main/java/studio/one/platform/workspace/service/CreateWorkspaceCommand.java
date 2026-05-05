package studio.one.platform.workspace.service;

import studio.one.platform.workspace.model.WorkspaceVisibility;

public record CreateWorkspaceCommand(
        String name,
        String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessContext actor) {
}
