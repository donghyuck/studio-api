package studio.one.platform.workspace.web.dto.request;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public record WorkspaceUpdateRequest(
        String name,
        WorkspaceVisibility visibility) {
}
