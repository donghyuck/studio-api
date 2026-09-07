package studio.one.platform.workspace.web.controller;

import jakarta.validation.Valid;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.web.dto.ApiResponse;
import studio.one.platform.workspace.application.command.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.application.command.WorkspaceAccessContext;
import studio.one.platform.workspace.application.command.WorkspaceListQuery;
import studio.one.platform.workspace.application.error.WorkspaceValidationException;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.domain.model.WorkspaceRef;
import studio.one.platform.workspace.domain.model.WorkspaceTreeNode;
import studio.one.platform.workspace.web.dto.request.WorkspaceCreateRequest;

@RestController
@RequestMapping("${studio.features.team.web.public-base-path:/api/teams}/{teamId}/workspaces")
@RequiredArgsConstructor
public class TeamWorkspaceController {

    private final WorkspaceTreeService treeService;
    private final TeamAuthorizationPort teamAuthorization;
    private final ObjectProvider<PrincipalResolver> principalResolverProvider;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<Page<WorkspaceRef>>> list(
            @PathVariable Long teamId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "rootOnly", required = false) Boolean rootOnly,
            @RequestParam(value = "archived", required = false) Boolean archived,
            @PageableDefault(size = 20, sort = "path", direction = Sort.Direction.ASC) Pageable pageable) {
        WorkspaceAccessContext actor = context();
        assertTeamGranted(teamId, actor, TeamPermissionActions.WORKSPACE_READ);
        return ResponseEntity.ok(ApiResponse.ok(treeService.list(
                new WorkspaceListQuery(q, null, teamId, parentId, rootOnly, archived), pageable, actor)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<WorkspaceRef>> createRoot(
            @PathVariable Long teamId,
            @Valid @RequestBody WorkspaceCreateRequest request) {
        WorkspaceAccessContext actor = context();
        assertTeamGranted(teamId, actor, TeamPermissionActions.WORKSPACE_CREATE);
        if (request.companyId() != null || request.teamId() != null && !teamId.equals(request.teamId())) {
            throw new WorkspaceValidationException("Workspace scope must match the Team path");
        }
        WorkspaceRef created = treeService.createRoot(new CreateRootWorkspaceCommand(
                null,
                teamId,
                request.name(),
                request.slug(),
                request.visibility(),
                request.accessMode(),
                actor));
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @GetMapping("/tree")
    @PreAuthorize("@endpointAuthz.can('features:team','read')")
    public ResponseEntity<ApiResponse<java.util.List<WorkspaceTreeNode>>> tree(@PathVariable Long teamId) {
        WorkspaceAccessContext actor = context();
        var trees = treeService.getRootsByTeamId(teamId, actor).stream()
                .map(root -> treeService.getTree(root.id(), actor))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(trees));
    }

    private WorkspaceAccessContext context() {
        PrincipalResolver resolver = principalResolverProvider.getIfAvailable();
        if (resolver == null) {
            throw new AuthenticationCredentialsNotFoundException("No principal resolver configured");
        }
        ApplicationPrincipal principal = resolver.currentOrNull();
        if (principal == null || principal.getUserId() == null || principal.getUserId() <= 0) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return new WorkspaceAccessContext(
                principal.getUserId(),
                principal.getUsername(),
                principal.hasRole("ROLE_ADMIN") || principal.hasRole("ADMIN"));
    }

    private void assertTeamGranted(
            Long teamId,
            WorkspaceAccessContext actor,
            String action) {
        if (!actor.platformAdmin()) {
            teamAuthorization.assertGranted(teamId, actor.requireUserId(), action);
        }
    }
}
