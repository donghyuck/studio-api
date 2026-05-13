package studio.one.platform.workspace.domain.model;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkspaceMemberRef)) {
            return false;
        }
        WorkspaceMemberRef that = (WorkspaceMemberRef) o;
        return inherited == that.inherited
                && Objects.equals(workspaceId, that.workspaceId)
                && Objects.equals(userId, that.userId)
                && role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, userId, role, inherited);
    }
}
