package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public class UpdateWorkspaceCommand {
    private final String name;
    private final WorkspaceVisibility visibility;
    private final WorkspaceAccessContext actor;

    public UpdateWorkspaceCommand(
            String name,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this.name = name;
        this.visibility = visibility;
        this.actor = actor;
    }

    public String name() {
        return name;
    }

    public WorkspaceVisibility visibility() {
        return visibility;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
