package studio.one.platform.workspace.web.dto.request;

import javax.validation.constraints.Positive;

public class WorkspaceParentChangeRequest {

    @Positive
    private Long newParentId;

    public WorkspaceParentChangeRequest() {
    }

    public WorkspaceParentChangeRequest(
            Long newParentId) {
        this.newParentId = newParentId;
    }

    public Long getNewParentId() { return newParentId; }

    public Long newParentId() { return newParentId; }

    public void setNewParentId(Long newParentId) { this.newParentId = newParentId; }


}
