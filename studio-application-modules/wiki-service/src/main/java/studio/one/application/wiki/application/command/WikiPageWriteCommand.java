package studio.one.application.wiki.application.command;

import studio.one.platform.workspace.application.command.WorkspaceAccessContext;

public class WikiPageWriteCommand {
    private final String title;
    private final String markdown;
    private final Long baseRevisionId;
    private final WorkspaceAccessContext actor;

    public WikiPageWriteCommand(
            String title,
            String markdown,
            Long baseRevisionId,
            WorkspaceAccessContext actor) {
        this.title = title;
        this.markdown = markdown;
        this.baseRevisionId = baseRevisionId;
        this.actor = actor;
    }

    public String title() {
        return title;
    }

    public String markdown() {
        return markdown;
    }

    public Long baseRevisionId() {
        return baseRevisionId;
    }

    public WorkspaceAccessContext actor() {
        return actor;
    }

}
