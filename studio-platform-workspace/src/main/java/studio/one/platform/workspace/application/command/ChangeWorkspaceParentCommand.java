package studio.one.platform.workspace.application.command;

public record ChangeWorkspaceParentCommand(
        Long newParentId,
        WorkspaceAccessContext actor) {
}
