package studio.one.platform.workspace.web.dto;

import jakarta.validation.constraints.NotBlank;

import studio.one.platform.workspace.model.WorkspaceVisibility;

public record WorkspaceCreateRequest(
        Long companyId,
        @NotBlank String name,
        @NotBlank String slug,
        WorkspaceVisibility visibility) {

    public WorkspaceCreateRequest(String name, String slug, WorkspaceVisibility visibility) {
        this(null, name, slug, visibility);
    }
}
