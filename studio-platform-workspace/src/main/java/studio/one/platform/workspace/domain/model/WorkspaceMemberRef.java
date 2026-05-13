package studio.one.platform.workspace.domain.model;

public class WorkspaceMemberRef {
    private final Long workspaceId;
    private final Long userId;
    private final WorkspaceRole role;
    private final boolean inherited;

    public WorkspaceMemberRef(
            Long workspaceId,
            Long userId,
            WorkspaceRole role,
            boolean inherited) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.inherited = inherited;
    }

    public Long workspaceId() {
        return workspaceId;
    }

    public Long userId() {
        return userId;
    }

    public WorkspaceRole role() {
        return role;
    }

    public boolean inherited() {
        return inherited;
    }

}
