package studio.one.application.wiki.service;

import studio.one.platform.workspace.service.WorkspaceAccessContext;

public record WikiPageArchiveCommand(
        Long baseRevisionId,
        WorkspaceAccessContext actor) {
}
