package studio.one.platform.workspace.domain.model;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public class WorkspaceRolePermissionMapping {
    private final WorkspaceRole role;
    private final String action;

    public WorkspaceRolePermissionMapping(
            WorkspaceRole role,
            String action) {
        this.role = role;
        this.action = action;
    }

    public WorkspaceRole role() {
        return role;
    }

    public String action() {
        return action;
    }

}
