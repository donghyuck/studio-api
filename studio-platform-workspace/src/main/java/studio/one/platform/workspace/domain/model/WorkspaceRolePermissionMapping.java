package studio.one.platform.workspace.domain.model;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public record WorkspaceRolePermissionMapping(
        WorkspaceRole role,
        String action) {
}
