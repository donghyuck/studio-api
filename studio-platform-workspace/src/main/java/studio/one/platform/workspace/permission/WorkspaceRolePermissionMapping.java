package studio.one.platform.workspace.permission;

import studio.one.platform.workspace.model.WorkspaceRole;

public record WorkspaceRolePermissionMapping(
        WorkspaceRole role,
        String action) {
}
