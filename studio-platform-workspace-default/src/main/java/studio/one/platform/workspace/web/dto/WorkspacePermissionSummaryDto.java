package studio.one.platform.workspace.web.dto;

import java.util.List;

import studio.one.platform.workspace.model.WorkspaceRole;

public record WorkspacePermissionSummaryDto(
        Long workspaceId,
        Long userId,
        WorkspaceRole effectiveRole,
        List<String> actions) {
}
