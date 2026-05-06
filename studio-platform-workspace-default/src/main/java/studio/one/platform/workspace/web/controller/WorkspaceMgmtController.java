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
import studio.one.platform.workspace.model.WorkspaceMemberRef;
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceTreeNode;
import studio.one.platform.workspace.permission.WorkspacePermissionDefinition;
import studio.one.platform.workspace.service.WorkspaceListQuery;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.web.dto.WorkspaceCreateRequest;
import studio.one.platform.workspace.web.dto.WorkspaceMemberRequest;
import studio.one.platform.workspace.web.dto.WorkspaceParentChangeRequest;
import studio.one.platform.workspace.web.dto.WorkspacePermissionSummaryDto;
import studio.one.platform.workspace.web.dto.WorkspaceUpdateRequest;

@RestController
@RequestMapping("${studio.features.workspace.web.mgmt-base-path:/api/mgmt/workspaces}")
@PreAuthorize("@endpointAuthz.can('features:workspace','manage') or hasRole('ADMIN')")
@Validated
public class WorkspaceMgmtController extends WorkspaceControllerSupport {

    public WorkspaceMgmtController(
            WorkspaceTreeService treeService,
            WorkspaceMemberService memberService,
            WorkspacePermissionService permissionService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        super(treeService, memberService, permissionService, principalResolverProvider);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WorkspaceRef>> createRoot(@Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(createRoot(request, true)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/children")
    public ResponseEntity<ApiResponse<WorkspaceRef>> createChild(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(createChild(workspaceId, request, true)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<WorkspaceRef>>> list(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "rootOnly", required = false) Boolean rootOnly,
            @RequestParam(value = "archived", required = false) Boolean archived,
            @PageableDefault(size = 20, sort = "path", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(list(
                new WorkspaceListQuery(q, parentId, rootOnly, archived),
                pageable,
                true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<WorkspaceRef>> get(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(get(workspaceId, true)));
    }

    @GetMapping("/by-path")
    public ResponseEntity<ApiResponse<WorkspaceRef>> getByPath(@RequestParam("path") String path) {
        return ResponseEntity.ok(ApiResponse.ok(getByPath(path, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/children")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> children(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(children(workspaceId, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/ancestors")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> ancestors(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(ancestors(workspaceId, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/descendants")
    public ResponseEntity<ApiResponse<List<WorkspaceRef>>> descendants(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(descendants(workspaceId, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/tree")
    public ResponseEntity<ApiResponse<WorkspaceTreeNode>> tree(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(tree(workspaceId, true)));
    }

    @PatchMapping("/{workspaceId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<WorkspaceRef>> update(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(update(workspaceId, request, true)));
    }

    @PatchMapping("/{workspaceId:[\\p{Digit}]+}/parent")
    public ResponseEntity<ApiResponse<WorkspaceRef>> changeParent(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceParentChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changeParent(workspaceId, request, true)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/archive")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable Long workspaceId) {
        archive(workspaceId, true);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/members")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberRef>>> members(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(directMembers(workspaceId, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/members/effective")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberRef>>> effectiveMembers(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(effectiveMembers(workspaceId, true)));
    }

    @PostMapping("/{workspaceId:[\\p{Digit}]+}/members")
    public ResponseEntity<ApiResponse<WorkspaceMemberRef>> addMember(
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(addMember(workspaceId, request, true)));
    }

    @PutMapping("/{workspaceId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<WorkspaceMemberRef>> changeRole(
            @PathVariable Long workspaceId,
            @PathVariable Long userId,
            @Valid @RequestBody WorkspaceMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(changeRole(workspaceId, userId, request, true)));
    }

    @DeleteMapping("/{workspaceId:[\\p{Digit}]+}/members/{userId:[\\p{Digit}]+}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long workspaceId,
            @PathVariable Long userId) {
        removeMember(workspaceId, userId, true);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/permissions/me")
    public ResponseEntity<ApiResponse<WorkspacePermissionSummaryDto>> permissionsMe(@PathVariable Long workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(myPermissions(workspaceId, true)));
    }

    @GetMapping("/{workspaceId:[\\p{Digit}]+}/permissions/actions")
    public ResponseEntity<ApiResponse<List<WorkspacePermissionDefinition>>> permissionActions(@PathVariable Long workspaceId) {
        get(workspaceId, true);
        return ResponseEntity.ok(ApiResponse.ok(permissionActions()));
    }
}
