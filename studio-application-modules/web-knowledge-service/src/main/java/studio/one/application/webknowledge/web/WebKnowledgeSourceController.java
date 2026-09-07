package studio.one.application.webknowledge.web;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.application.webknowledge.application.WebKnowledgeSourceCreateCommand;
import studio.one.application.webknowledge.application.WebKnowledgeCrawlRunView;
import studio.one.application.webknowledge.application.WebKnowledgePageView;
import studio.one.application.webknowledge.application.WebKnowledgePageDetailView;
import studio.one.application.webknowledge.application.WebKnowledgeSourceService;
import studio.one.application.webknowledge.application.WebKnowledgeSourceView;
import studio.one.application.webknowledge.application.WebKnowledgeSitePreviewService;
import studio.one.application.webknowledge.application.WebKnowledgeSitePreviewView;
import studio.one.application.webknowledge.infrastructure.web.WebPageFetchException;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;

@RestController
@RequestMapping("/api/workspaces/{workspaceId:[\\p{Digit}]+}/ai/rag/web-sources")
@Validated
public class WebKnowledgeSourceController {

    private static final String READ =
            "@endpointAuthz.can('features:workspace','read') and "
                    + "@endpointAuthz.can('services:ai_rag','read')";
    private static final String WRITE =
            "@endpointAuthz.can('features:workspace','read') and "
                    + "@endpointAuthz.can('services:ai_rag','write')";

    private final WebKnowledgeSourceService service;
    private final PrincipalResolver principalResolver;
    private final WorkspacePermissionService workspacePermissions;
    private final WebKnowledgeSitePreviewService previewService;

    public WebKnowledgeSourceController(
            WebKnowledgeSourceService service,
            PrincipalResolver principalResolver,
            WorkspacePermissionService workspacePermissions) {
        this(service, principalResolver, workspacePermissions, null);
    }

    public WebKnowledgeSourceController(
            WebKnowledgeSourceService service,
            PrincipalResolver principalResolver,
            WorkspacePermissionService workspacePermissions,
            WebKnowledgeSitePreviewService previewService) {
        this.service = service;
        this.principalResolver = principalResolver;
        this.workspacePermissions = workspacePermissions;
        this.previewService = previewService;
    }

    @PostMapping("/preview")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WebKnowledgeSitePreviewView>> preview(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WebKnowledgeSitePreviewRequest request) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.UPDATE);
        if (previewService == null) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_IMPLEMENTED,
                    "WEB_SITE_PREVIEW_UNAVAILABLE");
        }
        String principal = principalResolver.current().getUsername();
        WebKnowledgeSitePreviewView preview = invoke(() -> previewService.preview(
                request.url(),
                request.crawlPolicy() == null
                        ? studio.one.application.webknowledge.application.WebCrawlPolicyInput.defaults()
                        : request.crawlPolicy().toInput(),
                principal));
        return ResponseEntity.ok(ApiResponse.ok(preview));
    }

    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WebKnowledgeSourceView>> create(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WebKnowledgeSourceCreateRequest request) {
        var current = principalResolver.current();
        workspacePermissions.assertGranted(
                workspaceId, current.getUserId(), WorkspacePermissionActions.UPDATE);
        String principal = current.getUsername();
        WebKnowledgeSourceView created = invoke(() -> service.create(
                workspaceId,
                new WebKnowledgeSourceCreateCommand(
                        workspaceId,
                        request.url(),
                        request.displayName(),
                        request.embeddingDeploymentId(),
                        principal,
                        request.collectionMode(),
                        request.crawlPolicy() == null
                                ? studio.one.application.webknowledge.application.WebCrawlPolicyInput.defaults()
                                : request.crawlPolicy().toInput()),
                principal));
        return ResponseEntity.accepted()
                .location(URI.create("/api/workspaces/" + workspaceId
                        + "/ai/rag/web-sources/" + created.sourceId()))
                .body(ApiResponse.ok(created));
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WebKnowledgeSourceView>>> list(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) @Size(max = 160) String embeddingDeploymentId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.list(workspaceId, embeddingDeploymentId))));
    }

    @GetMapping("/{sourceId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WebKnowledgeSourceView>> get(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.get(workspaceId, sourceId))));
    }

    @PostMapping("/{sourceId}/refresh")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WebKnowledgeSourceView>> refresh(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.UPDATE);
        return ResponseEntity.accepted().body(ApiResponse.ok(invoke(() -> service.refresh(workspaceId, sourceId))));
    }

    @PostMapping("/{sourceId}/cancel")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WebKnowledgeSourceView>> cancel(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.UPDATE);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.cancel(workspaceId, sourceId))));
    }

    @GetMapping("/{sourceId}/crawl-runs")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WebKnowledgeCrawlRunView>>> listCrawlRuns(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.listCrawlRuns(workspaceId, sourceId))));
    }

    @GetMapping("/{sourceId}/crawl-runs/{runId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WebKnowledgeCrawlRunView>> getCrawlRun(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId,
            @PathVariable @Size(max = 80) String runId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(
                invoke(() -> service.getCrawlRun(workspaceId, sourceId, runId))));
    }

    @GetMapping("/{sourceId}/pages")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<WebKnowledgePageView>>> listPages(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.listPages(workspaceId, sourceId))));
    }

    @GetMapping("/{sourceId}/pages/{pageId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<WebKnowledgePageDetailView>> getPage(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId,
            @PathVariable @Size(max = 80) String pageId) {
        assertWorkspaceRead(workspaceId);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() -> service.getPage(workspaceId, sourceId, pageId))));
    }

    @PatchMapping("/{sourceId}/crawl-policy")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<WebKnowledgeSourceView>> updateCrawlPolicy(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId,
            @Valid @RequestBody WebCrawlPolicyRequest request) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.UPDATE);
        return ResponseEntity.ok(ApiResponse.ok(invoke(() ->
                service.updateCrawlPolicy(workspaceId, sourceId, request.toInput()))));
    }

    @DeleteMapping("/{sourceId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<ApiResponse<Void>> archive(
            @PathVariable Long workspaceId,
            @PathVariable @Size(max = 80) String sourceId) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.ARCHIVE);
        invoke(() -> {
            service.archive(workspaceId, sourceId);
            return null;
        });
        return ResponseEntity.ok(ApiResponse.ok());
    }

    private void assertWorkspaceRead(Long workspaceId) {
        assertWorkspaceAction(workspaceId, WorkspacePermissionActions.READ);
    }

    private void assertWorkspaceAction(Long workspaceId, String action) {
        workspacePermissions.assertGranted(
                workspaceId,
                principalResolver.current().getUserId(),
                action);
    }

    private <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "WEB_SOURCE_NOT_FOUND");
        } catch (WebPageFetchException | IllegalArgumentException ex) {
            String code = ex instanceof WebPageFetchException fetch
                    ? fetch.errorCode()
                    : "WEB_SOURCE_REQUEST_INVALID";
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, code);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "WEB_SOURCE_ALREADY_EXISTS");
        } catch (IllegalStateException ex) {
            if ("WEB_SOURCE_JOB_ALREADY_ACTIVE".equals(ex.getMessage())
                    || "WEB_SOURCE_COLLECTION_MODE_CONFLICT".equals(ex.getMessage())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, ex.getMessage());
            }
            if ("WEB_SITE_CRAWL_DISABLED".equals(ex.getMessage())
                    || "WEB_SITE_CRAWL_PARTITION_UNSUPPORTED".equals(ex.getMessage())
                    || "WEB_CRAWL_POLICY_REQUIRES_SITE_SOURCE".equals(ex.getMessage())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage());
            }
            if ("WEB_CRAWL_QUOTA_EXCEEDED".equals(ex.getMessage())
                    || "WEB_CRAWL_GLOBAL_LIMIT_EXCEEDED".equals(ex.getMessage())
                    || "WEB_CRAWL_WORKSPACE_LIMIT_EXCEEDED".equals(ex.getMessage())
                    || "WEB_CRAWL_PRINCIPAL_LIMIT_EXCEEDED".equals(ex.getMessage())
                    || "WEB_SITE_PREVIEW_BUSY".equals(ex.getMessage())) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
            }
            throw ex;
        }
    }
}
