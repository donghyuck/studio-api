package studio.one.platform.workspace.web.dto.request;

import javax.validation.constraints.NotNull;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public class WorkspaceMemberRequest {

    private Long userId;

    @NotNull
    private WorkspaceRole role;

    public WorkspaceMemberRequest() {
    }

    public WorkspaceMemberRequest(
            Long userId,
            WorkspaceRole role) {
        this.userId = userId;
        this.role = role;
    }

    public Long getUserId() { return userId; }

    public Long userId() { return userId; }

    public WorkspaceRole getRole() { return role; }

    public WorkspaceRole role() { return role; }

    public void setUserId(Long userId) { this.userId = userId; }


    public void setRole(WorkspaceRole role) { this.role = role; }


}
