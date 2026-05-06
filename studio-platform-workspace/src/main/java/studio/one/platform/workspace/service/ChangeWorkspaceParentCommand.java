package studio.one.platform.workspace.service;

public record ChangeWorkspaceParentCommand(
        Long newParentId,
        WorkspaceAccessContext actor) {
}
