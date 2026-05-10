package studio.one.platform.workspace.web.controller;

import java.util.List;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.workspace.domain.model.WorkspaceMemberRef;
import studio.one.platform.workspace.domain.model.WorkspaceRef;
import studio.one.platform.workspace.domain.model.WorkspaceRole;
import studio.one.platform.workspace.domain.model.WorkspaceTreeNode;
import studio.one.platform.workspace.domain.model.WorkspacePermissionDefinition;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.web.dto.request.WorkspaceActivateRequest;
import studio.one.platform.workspace.web.dto.request.WorkspaceArchiveRequest;
import studio.one.platform.workspace.web.dto.request.WorkspaceCreateRequest;
import studio.one.platform.workspace.web.dto.request.WorkspaceMemberRequest;
import studio.one.platform.workspace.web.dto.request.WorkspaceParentChangeRequest;
import studio.one.platform.workspace.web.dto.response.WorkspacePermissionSummaryDto;
import studio.one.platform.workspace.web.dto.request.WorkspaceUpdateRequest;

@RestController
@RequestMapping("${studio.features.workspace.web.public-base-path:/api/workspaces}")
@Validated
public class WorkspaceController extends WorkspaceControllerSupport {

    public WorkspaceController(
            WorkspaceTreeService treeService,
            WorkspaceMemberService memberService,
            WorkspacePermissionService permissionService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        super(treeService, memberService, permissionService, principalResolverProvider);
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> createRoot(@Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(createRoot(request, false)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/children")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> createChild(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(createChild(workspaceId, request, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> get(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(get(workspaceId, false)));
    }

    @GetMapping("/by-path")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> getByPath(
            @RequestParam(value = "companyId", required = false) Long companyId,
            @RequestParam("path") String path) {
        return ResponseEntity.ok(ApiResponse.ok(getByPath(companyId, path, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/children")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> children(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(children(workspaceId, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/ancestors")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> ancestors(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(ancestors(workspaceId, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/descendants")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> descendants(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(descendants(workspaceId, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/tree")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<WorkspaceTreeNode>> tree(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(tree(workspaceId, false)));
    }

    @PatchMapping("/{workspaceId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> update(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(update(workspaceId, request, false)));
    }

    @PatchMapping("/{workspaceId:[\\p{Digit}]+}/parent")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> changeParent(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceParentChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changeParent(workspaceId, request, false)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/archive")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<Void>> archive(
            @PathVariable Long workspaceId,
            @RequestBody(required = false) WorkspaceArchiveRequest request) {
        archive(workspaceId, request, false);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/activate")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> activate(
            @PathVariable Long workspaceId,
            @RequestBody(required = false) WorkspaceActivateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activate(workspaceId, request, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/members")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<Page<WorkspaceMemberRef>>> members(
            @PathVariable Long workspaceId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) WorkspaceRole role,
            @RequestParam(value = "inherited", required = false) Boolean inherited,
            @PageableDefault(size = 20, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(directMembers(
                workspaceId,
                q,
                keyword,
                role,
                inherited,
                pageable,
                false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/members/effective")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<Page<WorkspaceMemberRef>>> effectiveMembers(
            @PathVariable Long workspaceId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) WorkspaceRole role,
            @RequestParam(value = "inherited", required = false) Boolean inherited,
            @PageableDefault(size = 20, sort = "userId", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(effectiveMembers(
                workspaceId,
                q,
                keyword,
                role,
                inherited,
                pageable,
                false)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/members")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceMemberRef>> addMember(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(addMember(workspaceId, request, false)));
    }

    @PutMapping("/{workspaceId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<WorkspaceMemberRef>> changeRole(
            @PathVariable Long workspaceId,
            @PathVariable Long userId,
            @Valid @RequestBody WorkspaceMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changeRole(workspaceId, userId, request, false)));
    }

    @DeleteMapping("/{workspaceId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    @PreAuthorize("@endpointAuthz.can('features:workspace','write')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long workspaceId,
            @PathVariable Long userId) {
        removeMember(workspaceId, userId, false);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/permissions/me")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<WorkspacePermissionSummaryDto>> permissionsMe(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(myPermissions(workspaceId, false)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/permissions/actions")
    @PreAuthorize("@endpointAuthz.can('features:workspace','read')")
    public ResponseEntity<ApiResponse<List<WorkspacePermissionDefinition>>> permissionActions(@PathVariable Long workspaceId) {
        get(workspaceId, false);
        return ResponseEntity.ok(ApiResponse.ok(permissionActions()));
    }
}
