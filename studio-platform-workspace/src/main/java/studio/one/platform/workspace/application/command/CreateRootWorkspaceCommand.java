package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;

public class CreateRootWorkspaceCommand {

    private final Long companyId;
    private final String name;
    private final String slug;
    private final WorkspaceVisibility visibility;
    private final WorkspaceAccessContext actor;

    public CreateRootWorkspaceCommand(
            Long companyId,
            String name,
            String slug,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this.companyId = companyId;
        this.name = name;
        this.slug = slug;
        this.visibility = visibility;
        this.actor = actor;
    }

    public Long companyId() { return companyId; }

    public String name() { return name; }

    public String slug() { return slug; }

    public WorkspaceVisibility visibility() { return visibility; }

    public WorkspaceAccessContext actor() { return actor; }

public static CreateRootWorkspaceCommand from(CreateWorkspaceCommand command) {
        return new CreateRootWorkspaceCommand(
                null,
                command.name(),
                command.slug(),
                command.visibility(),
                command.actor());
    }

}
