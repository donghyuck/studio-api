package studio.one.platform.workspace.domain.model;

public record WorkspaceMemberRef(
        Long workspaceId,
        Long userId,
        WorkspaceRole role,
        boolean inherited) {
}
