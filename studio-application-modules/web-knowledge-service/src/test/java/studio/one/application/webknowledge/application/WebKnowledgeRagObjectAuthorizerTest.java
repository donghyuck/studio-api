package studio.one.application.webknowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceEntity;
import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;

class WebKnowledgeRagObjectAuthorizerTest {

    @Test
    void canWriteRequiresCurrentWorkspaceUpdatePermission() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        when(principals.currentOrNull()).thenReturn(principal(7L));
        when(sources.findById("wsrc-1")).thenReturn(Optional.of(source(false)));
        when(permissions.isGranted(2L, 7L, WorkspacePermissionActions.UPDATE)).thenReturn(true);
        WebKnowledgeRagObjectAuthorizer authorizer =
                new WebKnowledgeRagObjectAuthorizer(sources, principals, permissions);

        assertThat(authorizer.canWrite("web_source", "wsrc-1")).isTrue();
    }

    @Test
    void canWriteRejectsReadOnlyArchivedOrMissingSources() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        when(principals.currentOrNull()).thenReturn(principal(7L));
        when(sources.findById("wsrc-1")).thenReturn(Optional.of(source(false)));
        when(sources.findById("wsrc-archived")).thenReturn(Optional.of(source(true)));
        WebKnowledgeRagObjectAuthorizer authorizer =
                new WebKnowledgeRagObjectAuthorizer(sources, principals, permissions);

        assertThat(authorizer.canWrite("web_source", "wsrc-1")).isFalse();
        assertThat(authorizer.canWrite("web_source", "wsrc-archived")).isFalse();
        assertThat(authorizer.canWrite("web_source", "missing")).isFalse();
    }

    @Test
    void canReadStillUsesReadPermission() {
        WebKnowledgeSourceJpaRepository sources = mock(WebKnowledgeSourceJpaRepository.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        when(principals.currentOrNull()).thenReturn(principal(7L));
        when(sources.findById("wsrc-1")).thenReturn(Optional.of(source(false)));
        when(permissions.isGranted(2L, 7L, WorkspacePermissionActions.READ)).thenReturn(true);
        WebKnowledgeRagObjectAuthorizer authorizer =
                new WebKnowledgeRagObjectAuthorizer(sources, principals, permissions);

        assertThat(authorizer.canRead("web_source", "wsrc-1")).isTrue();
    }

    private WebKnowledgeSourceEntity source(boolean archived) {
        WebKnowledgeSourceEntity source = new WebKnowledgeSourceEntity(
                "wsrc-1",
                2L,
                "https://example.test",
                "https://example.test",
                "hash",
                "example.test",
                "Example",
                "embedding",
                null,
                "tester",
                Instant.parse("2026-09-23T00:00:00Z"));
        if (archived) {
            source.archive(Instant.parse("2026-09-23T00:01:00Z"));
        }
        return source;
    }

    private ApplicationPrincipal principal(Long userId) {
        return new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getUsername() {
                return "user-" + userId;
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("USER");
            }
        };
    }
}
