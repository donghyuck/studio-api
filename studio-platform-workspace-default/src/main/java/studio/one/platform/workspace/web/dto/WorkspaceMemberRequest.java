package studio.one.platform.workspace.web.dto;

import jakarta.validation.constraints.NotNull;

import studio.one.platform.workspace.model.WorkspaceRole;

public record WorkspaceMemberRequest(
        Long userId,
        @NotNull WorkspaceRole role) {
}
