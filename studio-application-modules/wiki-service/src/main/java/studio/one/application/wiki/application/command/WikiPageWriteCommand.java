package studio.one.application.wiki.application.command;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;

public record WikiPageWriteCommand(
        String title,
        String markdown,
        Long baseRevisionId,
        WorkspaceAccessContext actor) {
}
