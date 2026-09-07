package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceType;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.domain.model.TeamPermissionActions;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;

class DefaultTeamRagScopeResolverTest {

    @Test
    void buildsAuthorizedManifestFromReadableWorkspaceContributors() {
        PrincipalResolver principals = mock(PrincipalResolver.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principal.getUserId()).thenReturn(9L);
        when(principal.getUsername()).thenReturn("user");
        when(principals.currentOrNull()).thenReturn(principal);
        TeamAuthorizationPort authorization = mock(TeamAuthorizationPort.class);
        when(authorization.isMember(7L, 9L)).thenReturn(true);
        when(authorization.grantedActions(7L, 9L))
                .thenReturn(Set.of(TeamPermissionActions.KNOWLEDGE_READ));
        when(authorization.permissionVersion(7L)).thenReturn(3L);
        WorkspaceTreeService workspaces = mock(WorkspaceTreeService.class);
        when(workspaces.getAuthorizedTeamWorkspaceIds(eq(7L), eq(null), eq(100), any()))
                .thenReturn(List.of(2L, 3L));
        TeamKnowledgeSourceContributor contributor = mock(TeamKnowledgeSourceContributor.class);
        when(contributor.contribute(any())).thenReturn(List.of(new TeamKnowledgeSourceRef(
                7L, 2L, TeamKnowledgeSourceType.ATTACHMENT,
                "attachment", "10", "rev-1", Set.of())));
        DefaultTeamRagScopeResolver resolver = new DefaultTeamRagScopeResolver(
                principals, authorization, workspaces, List.of(contributor), 100, 32);

        var manifest = resolver.resolveAuthorized(7L, null);

        assertThat(manifest).isPresent();
        assertThat(manifest.orElseThrow().permissionVersion()).isEqualTo("3");
        assertThat(manifest.orElseThrow().corpusRevisionId()).startsWith("tcorpus-");
        assertThat(manifest.orElseThrow().sources()).hasSize(1);
    }

    @Test
    void hidesUnauthorizedOrContributorScopeEscape() {
        PrincipalResolver principals = mock(PrincipalResolver.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principal.getUserId()).thenReturn(9L);
        when(principals.currentOrNull()).thenReturn(principal);
        TeamAuthorizationPort authorization = mock(TeamAuthorizationPort.class);
        when(authorization.isMember(7L, 9L)).thenReturn(true);
        when(authorization.grantedActions(7L, 9L))
                .thenReturn(Set.of(TeamPermissionActions.KNOWLEDGE_READ));
        WorkspaceTreeService workspaces = mock(WorkspaceTreeService.class);
        when(workspaces.getAuthorizedTeamWorkspaceIds(eq(7L), eq(null), eq(100), any()))
                .thenReturn(List.of(2L));
        TeamKnowledgeSourceContributor contributor = mock(TeamKnowledgeSourceContributor.class);
        when(contributor.contribute(any())).thenReturn(List.of(new TeamKnowledgeSourceRef(
                7L, 99L, TeamKnowledgeSourceType.ATTACHMENT,
                "attachment", "10", "rev-1", Set.of())));
        DefaultTeamRagScopeResolver resolver = new DefaultTeamRagScopeResolver(
                principals, authorization, workspaces, List.of(contributor), 100, 32);

        assertThat(resolver.resolveAuthorized(7L, null)).isEmpty();
    }
}
