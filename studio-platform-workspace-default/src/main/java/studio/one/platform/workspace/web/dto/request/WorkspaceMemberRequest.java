package studio.one.platform.workspace.web.dto.request;

import jakarta.validation.constraints.NotNull;

import studio.one.platform.workspace.domain.model.WorkspaceRole;

public record WorkspaceMemberRequest(
        Long userId,
        @NotNull WorkspaceRole role) {
}
