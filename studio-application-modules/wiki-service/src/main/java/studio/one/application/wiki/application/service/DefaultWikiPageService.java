package studio.one.application.wiki.application.service;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.application.wiki.application.error.WikiConflictException;
import studio.one.application.wiki.application.error.WikiNotFoundException;
import studio.one.application.wiki.application.error.WikiValidationException;
import studio.one.application.wiki.domain.model.WikiPage;
import studio.one.application.wiki.domain.model.WikiPageSummary;
import studio.one.application.wiki.domain.model.WikiRevision;
import studio.one.application.wiki.domain.model.WikiRevisionSummary;
import studio.one.application.wiki.domain.model.WikiPermissionActions;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageEntity;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageJpaRepository;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageRevisionEntity;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageRevisionJpaRepository;
import studio.one.application.wiki.application.command.WikiPageArchiveCommand;
import studio.one.application.wiki.application.command.WikiPageRevertCommand;
import studio.one.application.wiki.application.usecase.WikiPageService;
import studio.one.application.wiki.application.command.WikiPageWriteCommand;
import studio.one.application.wiki.application.usecase.WikiRenderService;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

@RequiredArgsConstructor
public class DefaultWikiPageService implements WikiPageService {

    private static final int MAX_SLUG_LENGTH = 200;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final Pattern SLUG_PATTERN = Pattern.compile("[\\p{L}\\p{N}._-]+");
    private static final Set<String> ADMIN_PAGES = Set.of("_Sidebar", "_Footer");

    private final WikiPageJpaRepository pageRepository;
    private final WikiPageRevisionJpaRepository revisionRepository;
    private final WorkspacePermissionService permissionService;
    private final WikiRenderService renderService;

    @Override
    @Transactional(readOnly = true)
    public Page<WikiPageSummary> listPages(Long workspaceId, Pageable pageable, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, requireActor(actor), WikiPermissionActions.PAGE_READ);
        return pageRepository.findByWorkspaceIdAndArchivedFalse(workspaceId, pageable)
                .map(WikiPageEntity::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public WikiPage getPage(Long workspaceId, String pageSlug, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, requireActor(actor), WikiPermissionActions.PAGE_READ);
        WikiPageEntity page = activePage(workspaceId, normalizeSlug(pageSlug));
        WikiPageRevisionEntity revision = currentRevision(page);
        return toPage(page, revision);
    }

    @Override
    @Transactional
    public WikiPage putPage(Long workspaceId, String pageSlug, WikiPageWriteCommand command) {
        WorkspaceAccessContext actor = requireActor(command == null ? null : command.actor());
        String slug = normalizeSlug(pageSlug);
        String title = normalizeTitle(command == null ? null : command.title());
        String markdown = command == null || command.markdown() == null ? "" : command.markdown();
        Long baseRevisionId = command == null ? null : command.baseRevisionId();

        validateWorkspaceId(workspaceId);
        WikiPageEntity page = pageRepository.findByWorkspaceIdAndSlugForUpdate(workspaceId, slug)
                .orElse(null);
        boolean creating = page == null;
        String action = actionForWrite(slug, creating ? WikiPermissionActions.PAGE_CREATE : WikiPermissionActions.PAGE_UPDATE);
        permissionService.assertGranted(workspaceId, actor, action);

        if (creating) {
            if (baseRevisionId != null) {
                throw new WikiConflictException("Base revision must be omitted when creating a wiki page");
            }
            page = new WikiPageEntity();
            page.setWorkspaceId(workspaceId);
            page.setSlug(slug);
            page.setCreatedBy(actor.requireUserId());
        } else if (page.isArchived()) {
            throw new WikiNotFoundException("Wiki page is archived: " + slug);
        } else {
            assertCurrentRevision(page, baseRevisionId);
        }

        page.setTitle(title);
        page.setUpdatedBy(actor.requireUserId());
        page = pageRepository.saveAndFlush(page);

        int revisionNo = creating ? 1 : revisionRepository.maxRevisionNo(page.getPageId()) + 1;
        WikiPageRevisionEntity revision = createRevision(page, revisionNo, title, markdown, actor);
        revision = revisionRepository.saveAndFlush(revision);

        page.setCurrentRevisionId(revision.getRevisionId());
        page.setCurrentRevisionNo(revision.getRevisionNo());
        page = pageRepository.saveAndFlush(page);
        return toPage(page, revision);
    }

    @Override
    @Transactional
    public void archivePage(Long workspaceId, String pageSlug, WikiPageArchiveCommand command) {
        WorkspaceAccessContext resolved = requireActor(command == null ? null : command.actor());
        String slug = normalizeSlug(pageSlug);
        permissionService.assertGranted(workspaceId, resolved, actionForWrite(slug, WikiPermissionActions.PAGE_DELETE));
        WikiPageEntity page = activePageForUpdate(workspaceId, slug);
        assertCurrentRevision(page, command == null ? null : command.baseRevisionId());
        page.setArchived(true);
        page.setArchivedAt(Instant.now());
        page.setArchivedBy(resolved.requireUserId());
        page.setUpdatedBy(resolved.requireUserId());
        pageRepository.save(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WikiRevisionSummary> listRevisions(
            Long workspaceId,
            String pageSlug,
            Pageable pageable,
            WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, requireActor(actor), WikiPermissionActions.HISTORY_READ);
        WikiPageEntity page = page(workspaceId, normalizeSlug(pageSlug));
        return revisionRepository.findByPagePageIdOrderByRevisionNoDesc(page.getPageId(), pageable)
                .map(revision -> revision.toSummary(page.getSlug()));
    }

    @Override
    @Transactional(readOnly = true)
    public WikiRevision getRevision(Long workspaceId, String pageSlug, Long revisionId, WorkspaceAccessContext actor) {
        permissionService.assertGranted(workspaceId, requireActor(actor), WikiPermissionActions.HISTORY_READ);
        WikiPageEntity page = page(workspaceId, normalizeSlug(pageSlug));
        WikiPageRevisionEntity revision = revision(page, revisionId);
        return toRevision(page, revision);
    }

    @Override
    @Transactional
    public WikiPage revertRevision(Long workspaceId, String pageSlug, Long revisionId, WikiPageRevertCommand command) {
        WorkspaceAccessContext actor = requireActor(command == null ? null : command.actor());
        String slug = normalizeSlug(pageSlug);
        permissionService.assertGranted(workspaceId, actor, actionForWrite(slug, WikiPermissionActions.PAGE_REVERT));
        WikiPageEntity page = activePageForUpdate(workspaceId, slug);
        assertCurrentRevision(page, command == null ? null : command.baseRevisionId());
        WikiPageRevisionEntity source = revision(page, revisionId);

        int revisionNo = revisionRepository.maxRevisionNo(page.getPageId()) + 1;
        WikiPageRevisionEntity revision = createRevision(page, revisionNo, source.getTitle(), source.getMarkdown(), actor);
        revision = revisionRepository.saveAndFlush(revision);

        page.setTitle(revision.getTitle());
        page.setCurrentRevisionId(revision.getRevisionId());
        page.setCurrentRevisionNo(revision.getRevisionNo());
        page.setUpdatedBy(actor.requireUserId());
        page = pageRepository.saveAndFlush(page);
        return toPage(page, revision);
    }

    private WikiPageRevisionEntity createRevision(
            WikiPageEntity page,
            int revisionNo,
            String title,
            String markdown,
            WorkspaceAccessContext actor) {
        WikiPageRevisionEntity revision = new WikiPageRevisionEntity();
        revision.setPage(page);
        revision.setWorkspaceId(page.getWorkspaceId());
        revision.setRevisionNo(revisionNo);
        revision.setTitle(title);
        revision.setMarkdown(markdown);
        revision.setCreatedBy(actor.requireUserId());
        return revision;
    }

    private WikiPage toPage(WikiPageEntity page, WikiPageRevisionEntity revision) {
        return new WikiPage(
                page.getPageId(),
                page.getWorkspaceId(),
                page.getSlug(),
                page.getTitle(),
                revision.getMarkdown(),
                renderService.toSanitizedHtml(revision.getMarkdown()),
                page.getCurrentRevisionId(),
                page.getCurrentRevisionNo(),
                page.isArchived(),
                page.getCreatedAt(),
                page.getUpdatedAt());
    }

    private WikiRevision toRevision(WikiPageEntity page, WikiPageRevisionEntity revision) {
        return new WikiRevision(
                revision.getRevisionId(),
                page.getPageId(),
                page.getWorkspaceId(),
                page.getSlug(),
                revision.getTitle(),
                revision.getMarkdown(),
                renderService.toSanitizedHtml(revision.getMarkdown()),
                revision.getRevisionNo(),
                revision.getCreatedBy(),
                revision.getCreatedAt());
    }

    private WikiPageEntity activePage(Long workspaceId, String slug) {
        WikiPageEntity page = page(workspaceId, slug);
        if (page.isArchived()) {
            throw new WikiNotFoundException("Wiki page is archived: " + slug);
        }
        return page;
    }

    private WikiPageEntity activePageForUpdate(Long workspaceId, String slug) {
        WikiPageEntity page = pageForUpdate(workspaceId, slug);
        if (page.isArchived()) {
            throw new WikiNotFoundException("Wiki page is archived: " + slug);
        }
        return page;
    }

    private WikiPageEntity page(Long workspaceId, String slug) {
        validateWorkspaceId(workspaceId);
        return pageRepository.findByWorkspaceIdAndSlug(workspaceId, slug)
                .orElseThrow(() -> new WikiNotFoundException("Wiki page not found: " + slug));
    }

    private WikiPageEntity pageForUpdate(Long workspaceId, String slug) {
        validateWorkspaceId(workspaceId);
        return pageRepository.findByWorkspaceIdAndSlugForUpdate(workspaceId, slug)
                .orElseThrow(() -> new WikiNotFoundException("Wiki page not found: " + slug));
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new WikiValidationException("workspaceId is required");
        }
    }

    private WikiPageRevisionEntity currentRevision(WikiPageEntity page) {
        Long currentRevisionId = page.getCurrentRevisionId();
        if (currentRevisionId == null) {
            throw new WikiNotFoundException("Wiki current revision not found: " + page.getSlug());
        }
        return revision(page, currentRevisionId);
    }

    private WikiPageRevisionEntity revision(WikiPageEntity page, Long revisionId) {
        if (revisionId == null || revisionId <= 0) {
            throw new WikiValidationException("revisionId is required");
        }
        return revisionRepository.findByRevisionIdAndPagePageId(revisionId, page.getPageId())
                .orElseThrow(() -> new WikiNotFoundException("Wiki revision not found: " + revisionId));
    }

    private void assertCurrentRevision(WikiPageEntity page, Long baseRevisionId) {
        if (baseRevisionId == null || baseRevisionId <= 0) {
            throw new WikiConflictException("Wiki baseRevisionId is required");
        }
        if (!baseRevisionId.equals(page.getCurrentRevisionId())) {
            throw new WikiConflictException("Wiki baseRevisionId does not match current revision");
        }
    }

    private String actionForWrite(String slug, String defaultAction) {
        return ADMIN_PAGES.contains(slug) ? WikiPermissionActions.ADMIN : defaultAction;
    }

    private WorkspaceAccessContext requireActor(WorkspaceAccessContext actor) {
        if (actor == null) {
            throw new WikiValidationException("Wiki actor is required");
        }
        actor.requireUserId();
        return actor;
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new WikiValidationException("Wiki page slug is required");
        }
        String normalized = slug.trim();
        if (normalized.length() > MAX_SLUG_LENGTH || !SLUG_PATTERN.matcher(normalized).matches()) {
            throw new WikiValidationException("Wiki page slug is invalid");
        }
        return normalized;
    }

    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            throw new WikiValidationException("Wiki page title is required");
        }
        String normalized = title.trim();
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new WikiValidationException("Wiki page title is too long");
        }
        return normalized;
    }
}
