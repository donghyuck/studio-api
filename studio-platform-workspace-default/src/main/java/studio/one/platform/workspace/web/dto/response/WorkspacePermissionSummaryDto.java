package studio.one.platform.workspace.web.dto.response;

import java.util.List;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public record WorkspacePermissionSummaryDto(
        Long workspaceId,
        Long userId,
        WorkspaceRole effectiveRole,
        List<String> actions) {
}
