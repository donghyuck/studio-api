package studio.one.application.webknowledge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import studio.one.application.webknowledge.application.WebKnowledgeSourceService;
import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.domain.model.WorkspacePermissionActions;

class WebKnowledgeSourceControllerTest {

    @Test
    void rejectsWorkspaceWithoutMembershipBeforeListingSources() {
        WebKnowledgeSourceService service = mock(WebKnowledgeSourceService.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principals.current()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(7L);
        doThrow(new AccessDeniedException("denied"))
                .when(permissions)
                .assertGranted(2L, 7L, WorkspacePermissionActions.READ);

        WebKnowledgeSourceController controller =
                new WebKnowledgeSourceController(service, principals, permissions);

        assertThrows(AccessDeniedException.class, () -> controller.list(2L, null));
        verifyNoInteractions(service);
    }

    @Test
    void mapsCrossWorkspaceLookupToNonDisclosingNotFound() {
        WebKnowledgeSourceService service = mock(WebKnowledgeSourceService.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principals.current()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(7L);
        when(service.get(2L, "wsrc-hidden")).thenThrow(new NoSuchElementException("different workspace"));

        WebKnowledgeSourceController controller =
                new WebKnowledgeSourceController(service, principals, permissions);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.get(2L, "wsrc-hidden"));
        assertEquals(404, error.getStatusCode().value());
        assertEquals("WEB_SOURCE_NOT_FOUND", error.getReason());
    }

    @Test
    void requiresWorkspaceUpdatePermissionBeforeCreatingSource() {
        WebKnowledgeSourceService service = mock(WebKnowledgeSourceService.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principals.current()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(7L);
        doThrow(new AccessDeniedException("denied"))
                .when(permissions)
                .assertGranted(2L, 7L, WorkspacePermissionActions.UPDATE);
        WebKnowledgeSourceController controller =
                new WebKnowledgeSourceController(service, principals, permissions);

        assertThrows(AccessDeniedException.class, () -> controller.create(
                2L,
                new WebKnowledgeSourceCreateRequest(
                        "https://example.org/article",
                        "Reference",
                        "embedding-default")));

        verify(permissions).assertGranted(2L, 7L, WorkspacePermissionActions.UPDATE);
        verifyNoInteractions(service);
    }

    @Test
    void requiresWorkspaceArchivePermissionBeforeArchivingSource() {
        WebKnowledgeSourceService service = mock(WebKnowledgeSourceService.class);
        PrincipalResolver principals = mock(PrincipalResolver.class);
        WorkspacePermissionService permissions = mock(WorkspacePermissionService.class);
        ApplicationPrincipal principal = mock(ApplicationPrincipal.class);
        when(principals.current()).thenReturn(principal);
        when(principal.getUserId()).thenReturn(7L);
        doThrow(new AccessDeniedException("denied"))
                .when(permissions)
                .assertGranted(2L, 7L, WorkspacePermissionActions.ARCHIVE);
        WebKnowledgeSourceController controller =
                new WebKnowledgeSourceController(service, principals, permissions);

        assertThrows(AccessDeniedException.class, () -> controller.archive(2L, "wsrc-1"));

        verify(permissions).assertGranted(2L, 7L, WorkspacePermissionActions.ARCHIVE);
        verifyNoInteractions(service);
    }
}
