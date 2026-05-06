package studio.one.application.wiki.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.application.wiki.model.WikiPage;
import studio.one.application.wiki.model.WikiPageSummary;
import studio.one.application.wiki.model.WikiRevision;
import studio.one.application.wiki.model.WikiRevisionSummary;
import studio.one.platform.workspace.service.WorkspaceAccessContext;

public interface WikiPageService {

    Page<WikiPageSummary> listPages(Long workspaceId, Pageable pageable, WorkspaceAccessContext actor);

    WikiPage getPage(Long workspaceId, String pageSlug, WorkspaceAccessContext actor);

    WikiPage putPage(Long workspaceId, String pageSlug, WikiPageWriteCommand command);

    void archivePage(Long workspaceId, String pageSlug, WorkspaceAccessContext actor);

    Page<WikiRevisionSummary> listRevisions(Long workspaceId, String pageSlug, Pageable pageable, WorkspaceAccessContext actor);

    WikiRevision getRevision(Long workspaceId, String pageSlug, Long revisionId, WorkspaceAccessContext actor);

    WikiPage revertRevision(Long workspaceId, String pageSlug, Long revisionId, WikiPageRevertCommand command);
}
