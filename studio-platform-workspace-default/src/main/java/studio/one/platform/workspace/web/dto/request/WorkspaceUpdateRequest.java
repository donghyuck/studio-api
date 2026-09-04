package studio.one.platform.workspace.web.dto.request;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;

public record WorkspaceUpdateRequest(
        String name,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode) {

    public WorkspaceUpdateRequest(String name, WorkspaceVisibility visibility) {
        this(name, visibility, null);
    }
}
