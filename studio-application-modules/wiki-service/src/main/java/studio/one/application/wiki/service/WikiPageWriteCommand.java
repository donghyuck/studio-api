package studio.one.application.wiki.service;

import studio.one.platform.workspace.service.WorkspaceAccessContext;

public record WikiPageWriteCommand(
        String title,
        String markdown,
        Long baseRevisionId,
        WorkspaceAccessContext actor) {
}
