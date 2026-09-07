package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;

public record CreateWorkspaceCommand(
        String name,
        String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode,
        WorkspaceAccessContext actor) {

    public CreateWorkspaceCommand(
            String name,
            String slug,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this(name, slug, visibility, WorkspaceAccessMode.INHERIT, actor);
    }
}
