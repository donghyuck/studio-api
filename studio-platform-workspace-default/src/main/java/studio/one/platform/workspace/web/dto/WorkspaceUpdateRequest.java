package studio.one.platform.workspace.web.dto;

import studio.one.platform.workspace.model.WorkspaceVisibility;

public record WorkspaceUpdateRequest(
        String name,
        WorkspaceVisibility visibility) {
}
