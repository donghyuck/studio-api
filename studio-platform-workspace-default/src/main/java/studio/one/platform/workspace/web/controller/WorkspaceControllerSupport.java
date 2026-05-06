package studio.one.platform.workspace.web.controller;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.model.WorkspaceMemberRef;
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.model.WorkspaceTreeNode;
import studio.one.platform.workspace.permission.WorkspacePermissionActions;
import studio.one.platform.workspace.permission.WorkspacePermissionDefinition;
import studio.one.platform.workspace.service.ChangeWorkspaceParentCommand;
import studio.one.platform.workspace.service.CreateWorkspaceCommand;
import studio.one.platform.workspace.service.UpdateWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceListQuery;
import studio.one.platform.workspace.service.WorkspaceMemberCommand;
import studio.one.platform.workspace.service.WorkspaceMemberListQuery;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.web.dto.WorkspaceCreateRequest;
import studio.one.platform.workspace.web.dto.WorkspaceMemberRequest;
import studio.one.platform.workspace.web.dto.WorkspaceParentChangeRequest;
import studio.one.platform.workspace.web.dto.WorkspacePermissionSummaryDto;
import studio.one.platform.workspace.web.dto.WorkspaceUpdateRequest;

abstract class WorkspaceControllerSupport {

    private final WorkspaceTreeService treeService;
    private final WorkspaceMemberService memberService;
    private final WorkspacePermissionService permissionService;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    WorkspaceControllerSupport(
            WorkspaceTreeService treeService,
            WorkspaceMemberService memberService,
            WorkspacePermissionService permissionService,
            ObjectProvider<PrincipalResolver> principalResolverProvider) {
        this.treeService = treeService;
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.principalResolverProvider = principalResolverProvider;
    }

    WorkspaceRef createRoot(WorkspaceCreateRequest request, boolean platformAdmin) {
        return treeService.createRoot(new CreateWorkspaceCommand(
                request.name(),
                request.slug(),
                request.visibility(),
                context(platformAdmin)));
    }

    WorkspaceRef createChild(Long workspaceId, WorkspaceCreateRequest request, boolean platformAdmin) {
        return treeService.createChild(workspaceId, new CreateWorkspaceCommand(
                request.name(),
                request.slug(),
                request.visibility(),
                context(platformAdmin)));
    }

    WorkspaceRef update(Long workspaceId, WorkspaceUpdateRequest request, boolean platformAdmin) {
        return treeService.update(workspaceId, new UpdateWorkspaceCommand(
                request.name(),
                request.visibility(),
                context(platformAdmin)));
    }

    WorkspaceRef changeParent(Long workspaceId, WorkspaceParentChangeRequest request, boolean platformAdmin) {
        return treeService.changeParent(workspaceId, new ChangeWorkspaceParentCommand(
                request.newParentId(),
                context(platformAdmin)));
    }

    WorkspaceRef get(Long workspaceId, boolean platformAdmin) {
        return treeService.getById(workspaceId, context(platformAdmin));
    }

    WorkspaceRef getByPath(String path, boolean platformAdmin) {
        return treeService.getByPath(path, context(platformAdmin));
    }

    Page<WorkspaceRef> list(WorkspaceListQuery query, Pageable pageable, boolean platformAdmin) {
        return treeService.list(query, pageable, context(platformAdmin));
    }

    List<WorkspaceRef> children(Long workspaceId, boolean platformAdmin) {
        return treeService.getChildren(workspaceId, context(platformAdmin));
    }

    List<WorkspaceRef> ancestors(Long workspaceId, boolean platformAdmin) {
        return treeService.getAncestors(workspaceId, context(platformAdmin));
    }

    List<WorkspaceRef> descendants(Long workspaceId, boolean platformAdmin) {
        return treeService.getDescendants(workspaceId, context(platformAdmin));
    }

    WorkspaceTreeNode tree(Long workspaceId, boolean platformAdmin) {
        return treeService.getTree(workspaceId, context(platformAdmin));
    }

    void archive(Long workspaceId, boolean platformAdmin) {
        treeService.archive(workspaceId, context(platformAdmin));
    }

    WorkspaceMemberRef addMember(Long workspaceId, WorkspaceMemberRequest request, boolean platformAdmin) {
        return memberService.addMember(workspaceId, new WorkspaceMemberCommand(
                request.userId(),
                request.role(),
                context(platformAdmin)));
    }

    WorkspaceMemberRef changeRole(Long workspaceId, Long userId, WorkspaceMemberRequest request, boolean platformAdmin) {
        return memberService.changeRole(workspaceId, new WorkspaceMemberCommand(
                userId,
                request.role(),
                context(platformAdmin)));
    }

    void removeMember(Long workspaceId, Long userId, boolean platformAdmin) {
        memberService.removeMember(workspaceId, userId, context(platformAdmin));
    }

    List<WorkspaceMemberRef> directMembers(Long workspaceId, boolean platformAdmin) {
        return memberService.getDirectMembers(workspaceId, context(platformAdmin));
    }

    List<WorkspaceMemberRef> effectiveMembers(Long workspaceId, boolean platformAdmin) {
        return memberService.getEffectiveMembers(workspaceId, context(platformAdmin));
    }

    Page<WorkspaceMemberRef> directMembers(
            Long workspaceId,
            String q,
            String keyword,
            WorkspaceRole role,
            Boolean inherited,
            Pageable pageable,
            boolean platformAdmin) {
        return memberService.getDirectMembers(
                workspaceId,
                memberListQuery(q, keyword, role, inherited),
                pageable,
                context(platformAdmin));
    }

    Page<WorkspaceMemberRef> effectiveMembers(
            Long workspaceId,
            String q,
            String keyword,
            WorkspaceRole role,
            Boolean inherited,
            Pageable pageable,
            boolean platformAdmin) {
        return memberService.getEffectiveMembers(
                workspaceId,
                memberListQuery(q, keyword, role, inherited),
                pageable,
                context(platformAdmin));
    }

    WorkspacePermissionSummaryDto myPermissions(Long workspaceId, boolean platformAdmin) {
        WorkspaceAccessContext context = context(platformAdmin);
        permissionService.assertGranted(workspaceId, context, WorkspacePermissionActions.READ);
        return new WorkspacePermissionSummaryDto(
                workspaceId,
                context.userId(),
                permissionService.getEffectiveRole(workspaceId, context),
                permissionService.getGrantedActions(workspaceId, context));
    }

    List<WorkspacePermissionDefinition> permissionActions() {
        return permissionService.getPermissionDefinitions();
    }

    private WorkspaceMemberListQuery memberListQuery(
            String q,
            String keyword,
            WorkspaceRole role,
            Boolean inherited) {
        String resolvedKeyword = keyword != null && !keyword.isBlank() ? keyword : q;
        return new WorkspaceMemberListQuery(resolvedKeyword, role, inherited);
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
