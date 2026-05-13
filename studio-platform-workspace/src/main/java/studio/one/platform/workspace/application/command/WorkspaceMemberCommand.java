package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public class WorkspaceMemberCommand {
    private final Long userId;
    private final WorkspaceRole role;
    private final WorkspaceAccessContext actor;

    public WorkspaceMemberCommand(
            Long userId,
            WorkspaceRole role,
            WorkspaceAccessContext actor) {
        this.userId = userId;
        this.role = role;
        this.actor = actor;
    }

    public Long userId() {
        return userId;
    }

    public WorkspaceRole role() {
        return role;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
