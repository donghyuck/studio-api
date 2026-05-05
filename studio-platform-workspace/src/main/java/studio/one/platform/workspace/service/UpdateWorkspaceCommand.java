package studio.one.platform.workspace.service;

import studio.one.platform.workspace.model.WorkspaceVisibility;

public record UpdateWorkspaceCommand(
        String name,
        WorkspaceVisibility visibility,
        WorkspaceAccessContext actor) {
}
