package studio.one.platform.workspace.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;

public record WorkspaceCreateRequest(
        Long companyId,
        Long teamId,
        @NotBlank String name,
        @NotBlank String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode) {

    public WorkspaceCreateRequest(
            Long companyId,
            String name,
            String slug,
            WorkspaceVisibility visibility) {
        this(companyId, null, name, slug, visibility, WorkspaceAccessMode.INHERIT);
    }

    public WorkspaceCreateRequest(String name, String slug, WorkspaceVisibility visibility) {
        this(null, null, name, slug, visibility, WorkspaceAccessMode.INHERIT);
    }
}
