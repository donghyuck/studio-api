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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;

import studio.one.platform.identity.ApplicationPrincipal;
import studio.one.platform.identity.PrincipalResolver;
import studio.one.platform.workspace.model.WorkspaceRole;
import studio.one.platform.workspace.model.WorkspaceRef;
import studio.one.platform.workspace.model.WorkspaceVisibility;
import studio.one.platform.workspace.permission.WorkspacePermissionActions;
import studio.one.platform.workspace.service.ChangeWorkspaceParentCommand;
import studio.one.platform.workspace.service.CreateRootWorkspaceCommand;
import studio.one.platform.workspace.service.WorkspaceAccessContext;
import studio.one.platform.workspace.service.WorkspaceListQuery;
import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspaceMemberListQuery;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.web.dto.WorkspaceCreateRequest;
import studio.one.platform.workspace.web.dto.WorkspaceParentChangeRequest;

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
        when(treeService.createRoot(any(CreateRootWorkspaceCommand.class))).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.createRoot(new WorkspaceCreateRequest("Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateRootWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateRootWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        WorkspaceAccessContext actor = captor.getValue().actor();
        assertThat(captor.getValue().companyId()).isNull();
        assertThat(actor.userId()).isEqualTo(10L);
        assertThat(actor.platformAdmin()).isFalse();
    }

    @Test
    void userControllerRejectsCallerProvidedCompanyScopeOnRootCreation() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.createRoot(
                new WorkspaceCreateRequest(7L, "Acme", "acme", WorkspaceVisibility.PRIVATE)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userControllerDoesNotBypassWorkspacePermissionForPlatformAdminRole() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.createRoot(any(CreateRootWorkspaceCommand.class))).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", true));

        controller.createRoot(new WorkspaceCreateRequest("Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateRootWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateRootWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        assertThat(captor.getValue().actor().platformAdmin()).isFalse();
    }

    @Test
    void mgmtControllerUsesPlatformAdminAccessContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.createRoot(any(CreateRootWorkspaceCommand.class))).thenReturn(workspace());
        WorkspaceMgmtController controller = new WorkspaceMgmtController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", false));

        controller.createRoot(new WorkspaceCreateRequest(7L, "Acme", "acme", WorkspaceVisibility.PRIVATE));

        ArgumentCaptor<CreateRootWorkspaceCommand> captor = ArgumentCaptor.forClass(CreateRootWorkspaceCommand.class);
        verify(treeService).createRoot(captor.capture());
        assertThat(captor.getValue().companyId()).isEqualTo(7L);
        assertThat(captor.getValue().actor().platformAdmin()).isTrue();
    }

    @Test
    void mgmtControllerListUsesFiltersAndPlatformAdminAccessContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        var pageable = PageRequest.of(1, 5);
        when(treeService.list(any(), eq(pageable), any()))
                .thenReturn(new PageImpl<>(List.of(workspace()), pageable, 1));
        WorkspaceMgmtController controller = new WorkspaceMgmtController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", false));

        controller.list("acme", 7L, 3L, false, true, pageable);

        ArgumentCaptor<WorkspaceListQuery> queryCaptor = ArgumentCaptor.forClass(WorkspaceListQuery.class);
        ArgumentCaptor<WorkspaceAccessContext> contextCaptor = ArgumentCaptor.forClass(WorkspaceAccessContext.class);
        verify(treeService).list(queryCaptor.capture(), eq(pageable), contextCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo(new WorkspaceListQuery("acme", 7L, 3L, false, true));
        assertThat(contextCaptor.getValue().platformAdmin()).isTrue();
    }

    @Test
    void getByPathPassesCompanyScopeToService() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.getByPath(eq(7L), eq("acme"), any())).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.getByPath(7L, "acme");

        ArgumentCaptor<WorkspaceAccessContext> contextCaptor = ArgumentCaptor.forClass(WorkspaceAccessContext.class);
        verify(treeService).getByPath(eq(7L), eq("acme"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().platformAdmin()).isFalse();
    }

    @Test
    void parentChangeUsesAccessContextAndRequestParentId() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.changeParent(eq(1L), any())).thenReturn(workspace());
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.changeParent(1L, new WorkspaceParentChangeRequest(2L));

        ArgumentCaptor<ChangeWorkspaceParentCommand> captor =
                ArgumentCaptor.forClass(ChangeWorkspaceParentCommand.class);
        verify(treeService).changeParent(eq(1L), captor.capture());
        assertThat(captor.getValue().newParentId()).isEqualTo(2L);
        assertThat(captor.getValue().actor().platformAdmin()).isFalse();
    }

    @Test
    void mgmtParentChangeUsesPlatformAdminContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        when(treeService.changeParent(eq(1L), any())).thenReturn(workspace());
        WorkspaceMgmtController controller = new WorkspaceMgmtController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", false));

        controller.changeParent(1L, new WorkspaceParentChangeRequest(null));

        ArgumentCaptor<ChangeWorkspaceParentCommand> captor =
                ArgumentCaptor.forClass(ChangeWorkspaceParentCommand.class);
        verify(treeService).changeParent(eq(1L), captor.capture());
        assertThat(captor.getValue().newParentId()).isNull();
        assertThat(captor.getValue().actor().platformAdmin()).isTrue();
    }

    @Test
    void memberListUsesPageableQueryAndNonAdminContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        var pageable = PageRequest.of(1, 5);
        when(memberService.getDirectMembers(eq(1L), any(), eq(pageable), any()))
                .thenReturn(new PageImpl<>(List.of(member()), pageable, 1));
        WorkspaceController controller = new WorkspaceController(
                treeService,
                memberService,
                permissionService,
                principalProvider("user", false));

        controller.members(1L, "ali", null, WorkspaceRole.VIEWER, false, pageable);

        ArgumentCaptor<WorkspaceMemberListQuery> queryCaptor =
                ArgumentCaptor.forClass(WorkspaceMemberListQuery.class);
        ArgumentCaptor<WorkspaceAccessContext> contextCaptor = ArgumentCaptor.forClass(WorkspaceAccessContext.class);
        verify(memberService).getDirectMembers(eq(1L), queryCaptor.capture(), eq(pageable), contextCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo(new WorkspaceMemberListQuery("ali", WorkspaceRole.VIEWER, false));
        assertThat(contextCaptor.getValue().platformAdmin()).isFalse();
    }

    @Test
    void mgmtEffectiveMemberListUsesKeywordAliasAndPlatformAdminContext() {
        WorkspaceTreeService treeService = org.mockito.Mockito.mock(WorkspaceTreeService.class);
        WorkspaceMemberService memberService = org.mockito.Mockito.mock(WorkspaceMemberService.class);
        WorkspacePermissionService permissionService = org.mockito.Mockito.mock(WorkspacePermissionService.class);
        var pageable = PageRequest.of(0, 20);
        when(memberService.getEffectiveMembers(eq(1L), any(), eq(pageable), any()))
                .thenReturn(new PageImpl<>(List.of(member()), pageable, 1));
        WorkspaceMgmtController controller = new WorkspaceMgmtController(
                treeService,
                memberService,
                permissionService,
                principalProvider("admin", false));

        controller.effectiveMembers(1L, "ignored", "bob", WorkspaceRole.EDITOR, true, pageable);

        ArgumentCaptor<WorkspaceMemberListQuery> queryCaptor =
                ArgumentCaptor.forClass(WorkspaceMemberListQuery.class);
        ArgumentCaptor<WorkspaceAccessContext> contextCaptor = ArgumentCaptor.forClass(WorkspaceAccessContext.class);
        verify(memberService).getEffectiveMembers(eq(1L), queryCaptor.capture(), eq(pageable), contextCaptor.capture());
        assertThat(queryCaptor.getValue()).isEqualTo(new WorkspaceMemberListQuery("bob", WorkspaceRole.EDITOR, true));
        assertThat(contextCaptor.getValue().platformAdmin()).isTrue();
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

    private studio.one.platform.workspace.model.WorkspaceMemberRef member() {
        return new studio.one.platform.workspace.model.WorkspaceMemberRef(1L, 10L, WorkspaceRole.OWNER, false);
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
