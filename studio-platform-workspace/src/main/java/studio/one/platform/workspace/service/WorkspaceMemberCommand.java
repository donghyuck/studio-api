package studio.one.platform.workspace.service;

import studio.one.platform.workspace.model.WorkspaceRole;

public record WorkspaceMemberCommand(
        Long userId,
        WorkspaceRole role,
        WorkspaceAccessContext actor) {
}
