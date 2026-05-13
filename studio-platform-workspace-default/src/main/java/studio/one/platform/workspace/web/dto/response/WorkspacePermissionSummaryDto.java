package studio.one.platform.workspace.web.dto.response;

import java.util.List;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public class WorkspacePermissionSummaryDto {

    private final Long workspaceId;
    private final Long userId;
    private final WorkspaceRole effectiveRole;
    private final List<String> actions;

    public WorkspacePermissionSummaryDto(
            Long workspaceId,
            Long userId,
            WorkspaceRole effectiveRole,
            List<String> actions) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.effectiveRole = effectiveRole;
        this.actions = actions;
    }

    public Long workspaceId() { return workspaceId; }

    public Long userId() { return userId; }

    public WorkspaceRole effectiveRole() { return effectiveRole; }

    public List<String> actions() { return actions; }

}
