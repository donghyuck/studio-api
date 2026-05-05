package studio.one.platform.workspace.web.dto;

import jakarta.validation.constraints.NotBlank;

import studio.one.platform.workspace.model.WorkspaceVisibility;

public record WorkspaceCreateRequest(
        @NotBlank String name,
        @NotBlank String slug,
        WorkspaceVisibility visibility) {
}
