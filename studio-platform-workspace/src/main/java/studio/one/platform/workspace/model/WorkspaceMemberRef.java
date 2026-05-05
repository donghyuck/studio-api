package studio.one.platform.workspace.model;

public record WorkspaceMemberRef(
        Long workspaceId,
        Long userId,
        WorkspaceRole role,
        boolean inherited) {
}
