package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public class CreateWorkspaceCommand {
    private final String name;
    private final String slug;
    private final WorkspaceVisibility visibility;
    private final WorkspaceAccessContext actor;

    public CreateWorkspaceCommand(
            String name,
            String slug,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this.name = name;
        this.slug = slug;
        this.visibility = visibility;
        this.actor = actor;
    }

    public String name() {
        return name;
    }

    public String slug() {
        return slug;
    }

    public WorkspaceVisibility visibility() {
        return visibility;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
