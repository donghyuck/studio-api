package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public record WorkspaceMemberCommand(
        Long userId,
        WorkspaceRole role,
        WorkspaceAccessContext actor) {
}
