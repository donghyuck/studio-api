package studio.one.application.wiki.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.wiki.application.command.WikiPageArchiveCommand;
import studio.one.application.wiki.application.command.WikiPageRevertCommand;
import studio.one.application.wiki.application.command.WikiPageWriteCommand;
import studio.one.application.wiki.domain.model.WikiPage;
import studio.one.application.wiki.domain.model.WikiPageSummary;
import studio.one.application.wiki.domain.model.WikiRevision;
import studio.one.application.wiki.domain.model.WikiRevisionSummary;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;

public interface WikiPageService {

    Page<WikiPageSummary> listPages(Long workspaceId, Pageable pageable, WorkspaceAccessContext actor);

    WikiPage getPage(Long workspaceId, String pageSlug, WorkspaceAccessContext actor);

    WikiPage putPage(Long workspaceId, String pageSlug, WikiPageWriteCommand command);

    void archivePage(Long workspaceId, String pageSlug, WikiPageArchiveCommand command);

    Page<WikiRevisionSummary> listRevisions(Long workspaceId, String pageSlug, Pageable pageable, WorkspaceAccessContext actor);

    WikiRevision getRevision(Long workspaceId, String pageSlug, Long revisionId, WorkspaceAccessContext actor);

    WikiPage revertRevision(Long workspaceId, String pageSlug, Long revisionId, WikiPageRevertCommand command);
}
