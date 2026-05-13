package studio.one.application.wiki.application.command;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;

public class WikiPageArchiveCommand {
    private final Long baseRevisionId;
    private final WorkspaceAccessContext actor;

    public WikiPageArchiveCommand(
            Long baseRevisionId,
            WorkspaceAccessContext actor) {
        this.baseRevisionId = baseRevisionId;
        this.actor = actor;
    }

    public Long baseRevisionId() {
        return baseRevisionId;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
