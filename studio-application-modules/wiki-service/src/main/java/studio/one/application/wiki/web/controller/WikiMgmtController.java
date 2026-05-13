package studio.one.application.wiki.web.controller;

import javax.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.application.wiki.application.usecase.WikiPageService;
import studio.one.application.wiki.web.dto.request.WikiArchiveRequest;
import studio.one.application.wiki.web.dto.response.WikiPageDto;
import studio.one.application.wiki.web.dto.response.WikiPageSummaryDto;
import studio.one.application.wiki.web.dto.request.WikiPageWriteRequest;
import studio.one.application.wiki.web.dto.request.WikiRevertRequest;
import studio.one.application.wiki.web.dto.response.WikiRevisionDto;
import studio.one.application.wiki.web.dto.response.WikiRevisionSummaryDto;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.wiki.web.mgmt-base-path:/api/mgmt/workspaces}/{workspaceId:[\\p{Digit}]+}/wiki")
@PreAuthorize("@endpointAuthz.can('features:workspace','manage') or hasRole('ADMIN')")
@Validated
public class WikiMgmtController extends WikiControllerSupport {

    public WikiMgmtController(
            WikiPageService wikiPageService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        super(wikiPageService, principalResolverProvider);
    }

    @GetMapping("/pages")
    public ResponseEntity<ApiResponse<Page<WikiPageSummaryDto>>> pages(
            @PathVariable Long workspaceId,
            @PageableDefault(size = 20, sort = "slug", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(listPages(workspaceId, pageable, true)));
    }

    @GetMapping("/pages/{pageSlug}")
    public ResponseEntity<ApiResponse<WikiPageDto>> page(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug) {
        return ResponseEntity.ok(ApiResponse.ok(getPage(workspaceId, pageSlug, true)));
    }

    @PutMapping("/pages/{pageSlug}")
    public ResponseEntity<ApiResponse<WikiPageDto>> putPage(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug,
            @Valid @RequestBody WikiPageWriteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(putPage(workspaceId, pageSlug, request, true)));
    }

    @DeleteMapping("/pages/{pageSlug}")
    public ResponseEntity<ApiResponse<Void>> archivePage(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug,
            @Valid @RequestBody(required = false) WikiArchiveRequest request) {
        archivePage(workspaceId, pageSlug, request, true);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/pages/{pageSlug}/revisions")
    public ResponseEntity<ApiResponse<Page<WikiRevisionSummaryDto>>> revisions(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug,
            @PageableDefault(size = 20, sort = "revisionNo", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(listRevisions(workspaceId, pageSlug, pageable, true)));
    }

    @GetMapping("/pages/{pageSlug}/revisions/{revisionId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<WikiRevisionDto>> revision(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug,
            @PathVariable Long revisionId) {
        return ResponseEntity.ok(ApiResponse.ok(getRevision(workspaceId, pageSlug, revisionId, true)));
    }

    @PostMapping("/pages/{pageSlug}/revisions/{revisionId:[\\p{Digit}]+}/revert")
    public ResponseEntity<ApiResponse<WikiPageDto>> revert(
            @PathVariable Long workspaceId,
            @PathVariable String pageSlug,
            @PathVariable Long revisionId,
            @Valid @RequestBody(required = false) WikiRevertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(revertRevision(workspaceId, pageSlug, revisionId, request, true)));
    }
}
