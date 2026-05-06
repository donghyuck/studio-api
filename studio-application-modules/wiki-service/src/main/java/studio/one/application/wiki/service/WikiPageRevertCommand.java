package studio.one.application.wiki.service;

import studio.one.platform.workspace.service.WorkspaceAccessContext;

public record WikiPageRevertCommand(
        Long baseRevisionId,
        WorkspaceAccessContext actor) {
}
