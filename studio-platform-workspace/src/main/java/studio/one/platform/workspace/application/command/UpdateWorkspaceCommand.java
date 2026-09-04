package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;

public record UpdateWorkspaceCommand(
        String name,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode,
        WorkspaceAccessContext actor) {

    public UpdateWorkspaceCommand(
            String name,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this(name, visibility, null, actor);
    }
}
