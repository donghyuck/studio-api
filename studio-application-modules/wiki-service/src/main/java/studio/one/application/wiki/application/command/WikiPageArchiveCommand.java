package studio.one.application.wiki.application.command;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;

public record WikiPageArchiveCommand(
        Long baseRevisionId,
        WorkspaceAccessContext actor) {
}
