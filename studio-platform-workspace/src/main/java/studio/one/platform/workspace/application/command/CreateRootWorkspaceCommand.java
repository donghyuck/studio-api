package studio.one.platform.workspace.application.command;

import studio.one.platform.workspace.domain.model.WorkspaceVisibility;
import studio.one.platform.workspace.domain.model.WorkspaceAccessMode;

public record CreateRootWorkspaceCommand(
        Long companyId,
        Long teamId,
        String name,
        String slug,
        WorkspaceVisibility visibility,
        WorkspaceAccessMode accessMode,
        WorkspaceAccessContext actor) {

    public CreateRootWorkspaceCommand(
            Long companyId,
            String name,
            String slug,
            WorkspaceVisibility visibility,
            WorkspaceAccessContext actor) {
        this(companyId, null, name, slug, visibility, WorkspaceAccessMode.INHERIT, actor);
    }

    public static CreateRootWorkspaceCommand from(CreateWorkspaceCommand command) {
        return new CreateRootWorkspaceCommand(
                null,
                null,
                command.name(),
                command.slug(),
                command.visibility(),
                command.accessMode(),
                command.actor());
    }
}
