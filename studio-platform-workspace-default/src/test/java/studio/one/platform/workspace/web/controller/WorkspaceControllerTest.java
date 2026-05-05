package studio.one.platform.workspace.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestMapping;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceVisibility;
import studio.one.platform.workspace.permission.WorkspacePermissionActions;
import studio.one.platform.workspace.service.CreateWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.web.dto.WorkspaceCreateRequest;

class WorkspaceControllerTest {

    @Test
    void controllersUseSeparatedBasePathProperties() {
        assertThat(WorkspaceController.class.getAnnotation(RequestMapping.class).value())
                .contains("${studio.features.workspace.web.public-base-path:/api/workspaces}");
        assertThat(WorkspaceMgmtController.class.getAnnotation(RequestMapping.class).value())
                .contains("${studio.features.workspace.web.mgmt-base-path:/api/mgmt/workspaces}");
    }

    @Test
    void userControllerUsesNonAdminAccessContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.createRoot(any())).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.createRoot(new WorkspaceCreateRequest("Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        WorkspaceAccessContext actor = captor.getValue().actor();
        assertThat(actor.userId()).isEqualTo(10L);
        assertThat(actor.platformAdmin()).isFalse();
    }

    @Test
    void userControllerDoesNotBypassWorkspacePermissionForPlatformAdminRole() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.createRoot(any())).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", true));

        controller.createRoot(new WorkspaceCreateRequest("Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        assertThat(captor.getValue().actor().platformAdmin()).isFalse();
    }

    @Test
    void mgmtControllerUsesPlatformAdminAccessContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.createRoot(any())).thenReturn(workspace());
        WorkspaceMgmtController controller = new WorkspaceMgmtController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", false));

        controller.createRoot(new WorkspaceCreateRequest("Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        assertThat(captor.getValue().actor().platformAdmin()).isTrue();
    }

    @Test
    void permissionsMeChecksReadPermission() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(permissionService.getEffectiveRole(eq(1L), any(WorkspaceAccessContext.class)))
                .thenReturn(WorkspaceRole.VIEWER);
        when(permissionService.getGrantedActions(eq(1L), any(WorkspaceAccessContext.class)))
                .thenReturn(List.of(WorkspacePermissionActions.READ));
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.permissionsMe(1L);

        verify(permissionService).assertGranted(
                eq(1L),
                any(WorkspaceAccessContext.class),
                eq(WorkspacePermissionActions.READ));
    }

    private WorkspaceRef workspace() {
        return new WorkspaceRef(1L, null, 1L, "Acme", "acme", "acme", 0, WorkspaceVisibility.PRIVATE, false);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PrincipalResolver> principalProvider(String username, boolean adminRole) {
        ObjectProvider<PrincipalResolver> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        PrincipalResolver resolver = () -> new ApplicationPrincipal() {
            @Override
            public Long getUserId() {
                return 10L;
            }

            @Override
            public String getUsername() {
                return username;
            }

            @Override
            public Set<String> getRoles() {
                return adminRole ? Set.of("ADMIN") : Set.of();
            }
        };
        when(provider.getIfAvailable()).thenReturn(resolver);
        return provider;
    }
}
