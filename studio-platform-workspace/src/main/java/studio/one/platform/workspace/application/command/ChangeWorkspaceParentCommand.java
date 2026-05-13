package studio.one.platform.workspace.application.command;

public class ChangeWorkspaceParentCommand {
    private final Long newParentId;
    private final WorkspaceAccessContext actor;

    public ChangeWorkspaceParentCommand(
            Long newParentId,
            WorkspaceAccessContext actor) {
        this.newParentId = newParentId;
        this.actor = actor;
    }

    public Long newParentId() {
        return newParentId;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
