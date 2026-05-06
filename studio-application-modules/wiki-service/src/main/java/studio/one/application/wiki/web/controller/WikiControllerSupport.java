package studio.one.application.wiki.web.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import studio.one.application.wiki.service.WikiPageArchiveCommand;
import studio.one.application.wiki.service.WikiPageRevertCommand;
import studio.one.application.wiki.service.WikiPageService;
import studio.one.application.wiki.service.WikiPageWriteCommand;
import studio.one.application.wiki.web.dto.WikiArchiveRequest;
import studio.one.application.wiki.web.dto.WikiPageDto;
import studio.one.application.wiki.web.dto.WikiPageSummaryDto;
import studio.one.application.wiki.web.dto.WikiPageWriteRequest;
import studio.one.application.wiki.web.dto.WikiRevertRequest;
import studio.one.application.wiki.web.dto.WikiRevisionDto;
import studio.one.application.wiki.web.dto.WikiRevisionSummaryDto;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.service.WorkspaceAccessContext;

abstract class WikiControllerSupport {

    private final WikiPageService wikiPageService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    WikiControllerSupport(
            WikiPageService wikiPageService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        this.wikiPageService = wikiPageService;
        this.principalResolverProvider = principalResolverProvider;
    }

    Page<WikiPageSummaryDto> listPages(Long workspaceId, Pageable pageable, boolean platformAdmin) {
        return wikiPageService.listPages(workspaceId, pageable, context(platformAdmin))
                .map(WikiPageSummaryDto::from);
    }

    WikiPageDto getPage(Long workspaceId, String pageSlug, boolean platformAdmin) {
        return WikiPageDto.from(wikiPageService.getPage(workspaceId, pageSlug, context(platformAdmin)));
    }

    WikiPageDto putPage(Long workspaceId, String pageSlug, WikiPageWriteRequest request, boolean platformAdmin) {
        return WikiPageDto.from(wikiPageService.putPage(
                workspaceId,
                pageSlug,
                new WikiPageWriteCommand(
                        request.title(),
                        request.markdown(),
                        request.baseRevisionId(),
                        context(platformAdmin))));
    }

    void archivePage(Long workspaceId, String pageSlug, WikiArchiveRequest request, boolean platformAdmin) {
        wikiPageService.archivePage(
                workspaceId,
                pageSlug,
                new WikiPageArchiveCommand(
                        request == null ? null : request.baseRevisionId(),
                        context(platformAdmin)));
    }

    Page<WikiRevisionSummaryDto> listRevisions(
            Long workspaceId,
            String pageSlug,
            Pageable pageable,
            boolean platformAdmin) {
        return wikiPageService.listRevisions(workspaceId, pageSlug, pageable, context(platformAdmin))
                .map(WikiRevisionSummaryDto::from);
    }

    WikiRevisionDto getRevision(Long workspaceId, String pageSlug, Long revisionId, boolean platformAdmin) {
        return WikiRevisionDto.from(wikiPageService.getRevision(
                workspaceId,
                pageSlug,
                revisionId,
                context(platformAdmin)));
    }

    WikiPageDto revertRevision(
            Long workspaceId,
            String pageSlug,
            Long revisionId,
            WikiRevertRequest request,
            boolean platformAdmin) {
        return WikiPageDto.from(wikiPageService.revertRevision(
                workspaceId,
                pageSlug,
                revisionId,
                new WikiPageRevertCommand(
                        request == null ? null : request.baseRevisionId(),
                        context(platformAdmin))));
    }

    WorkspaceAccessContext context(boolean platformAdmin) {
        ApplicationPrincipal principal = principal();
        Long userId = principal.getUserId();
        if (userId == null || userId <= 0) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return new WorkspaceAccessContext(userId, principal.getUsername(), platformAdmin);
    }

    private ApplicationPrincipal principal() {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new AuthenticationCredentialsNotFoundException("No principal resolver configured");
        }
        ApplicationPrincipal principal = resolver.currentOrNull();
        if (principal == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return principal;
    }
}
