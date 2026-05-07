package studio.one.base.user.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;

import studio.one.base.user.company.model.CompanyMemberRef;
import studio.one.base.user.company.model.CompanyMemberStatus;
import studio.one.base.user.company.model.CompanyPermissionPolicyRef;
import studio.one.base.user.company.model.CompanyPermissionRolePolicyRef;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.model.CompanyStatus;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyPermissionService;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.base.user.web.dto.CompanyDto;
import studio.one.base.user.web.dto.CompanyMemberRequest;
import studio.one.base.user.web.dto.CompanyMemberRoleRequest;
import studio.one.base.user.web.dto.CompanyPermissionPolicyUpdateRequest;
import studio.one.base.user.web.dto.CompanyPermissionRolePolicyRequest;
import studio.one.base.user.web.dto.CompanyUpdateRequest;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

class CompanyMgmtControllerTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long ACTOR_ID = 99L;

    private ApplicationCompanyService companyService;
    private ApplicationCompanyMemberService memberService;
    private ApplicationCompanyPermissionService permissionService;
    private CompanyMgmtController controller;
    private org.springframework.security.core.userdetails.UserDetails principal;

    @BeforeEach
    void setUp() {
        companyService = mock(ApplicationCompanyService.class);
        memberService = mock(ApplicationCompanyMemberService.class);
        permissionService = mock(ApplicationCompanyPermissionService.class);
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(ACTOR_ID, "actor", Set.of())));
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<>(identityService),
                new SingletonObjectProvider<Environment>(null));
        principal = User.withUsername("actor").password("n/a").authorities("ROLE_USER").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCompanyRequiresCompanyReadPermission() {
        when(companyService.get(COMPANY_ID)).thenReturn(company());

        controller.get(COMPANY_ID, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.READ);
    }

    @Test
    void updateCompanyRequiresCompanyUpdatePermission() {
        when(companyService.update(eq(COMPANY_ID), org.mockito.ArgumentMatchers.any())).thenReturn(company());

        controller.update(
                COMPANY_ID,
                new CompanyUpdateRequest("Acme", "acme.test", "description", java.util.Map.of()),
                principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.UPDATE);
    }

    @Test
    void memberMutationRequiresCompanyMemberManagePermission() {
        when(memberService.addMember(COMPANY_ID, 7L, CompanyRole.ADMIN, ACTOR_ID, false)).thenReturn(member());

        controller.addMember(COMPANY_ID, new CompanyMemberRequest(7L, CompanyRole.ADMIN), principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void memberRoleChangeRequiresCompanyMemberManagePermission() {
        when(memberService.changeRole(COMPANY_ID, 7L, CompanyRole.MEMBER, ACTOR_ID, false)).thenReturn(member());

        controller.changeRole(COMPANY_ID, 7L, new CompanyMemberRoleRequest(CompanyRole.MEMBER), principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void memberRemovalRequiresCompanyMemberManagePermission() {
        controller.removeMember(COMPANY_ID, 7L, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void archiveRequiresCompanyArchivePermission() {
        when(companyService.archive(COMPANY_ID, ACTOR_ID)).thenReturn(company());

        controller.archive(COMPANY_ID, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.ARCHIVE);
    }

    @Test
    void membersRequiresCompanyMemberReadPermission() {
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_READ);
    }

    @Test
    void permissionSummaryRequiresCompanyPermissionReadPermission() {
        when(permissionService.getGrantedActions(COMPANY_ID, ACTOR_ID)).thenReturn(List.of(CompanyPermissionActions.READ));

        controller.permissionsMe(COMPANY_ID, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.PERMISSION_READ);
    }

    @Test
    void permissionActionsRequiresCompanyPermissionReadPermission() {
        when(companyService.get(COMPANY_ID)).thenReturn(company());

        controller.permissionActions(COMPANY_ID, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.PERMISSION_READ);
    }

    @Test
    void permissionPolicyRequiresCompanyPermissionReadPermission() {
        when(permissionService.getPolicy(COMPANY_ID)).thenReturn(permissionPolicy(false));

        var response = controller.permissionPolicy(COMPANY_ID, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.PERMISSION_READ);
        assertThat(response.getBody().getData().roles()).hasSize(1);
        assertThat(response.getBody().getData().roles().get(0).override()).isFalse();
    }

    @Test
    void permissionPolicyUpdateRequiresCompanyPermissionManagePermission() {
        when(permissionService.updatePolicy(eq(COMPANY_ID), org.mockito.ArgumentMatchers.any(), eq(ACTOR_ID), eq(false)))
                .thenReturn(permissionPolicy(true));

        var response = controller.updatePermissionPolicy(
                COMPANY_ID,
                new CompanyPermissionPolicyUpdateRequest(List.of(new CompanyPermissionRolePolicyRequest(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        true))),
                principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.PERMISSION_MANAGE);
        assertThat(response.getBody().getData().roles().get(0).override()).isTrue();
    }

    @Test
    void platformAdminBypassesPermissionPolicyUpdateObjectPermission() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(permissionService.updatePolicy(eq(COMPANY_ID), org.mockito.ArgumentMatchers.any(), eq(ACTOR_ID), eq(true)))
                .thenReturn(permissionPolicy(true));

        controller.updatePermissionPolicy(
                COMPANY_ID,
                new CompanyPermissionPolicyUpdateRequest(List.of(new CompanyPermissionRolePolicyRequest(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        true))),
                principal);

        org.mockito.Mockito.verify(permissionService, org.mockito.Mockito.never())
                .assertGranted(eq(COMPANY_ID), eq(ACTOR_ID), eq(CompanyPermissionActions.PERMISSION_MANAGE));
    }

    @Test
    void platformAdminBypassIsPassedToMemberMutations() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(memberService.addMember(COMPANY_ID, 7L, CompanyRole.OWNER, ACTOR_ID, true)).thenReturn(member());
        when(memberService.changeRole(COMPANY_ID, 7L, CompanyRole.OWNER, ACTOR_ID, true)).thenReturn(member());

        controller.addMember(COMPANY_ID, new CompanyMemberRequest(7L, CompanyRole.OWNER), principal);
        controller.changeRole(COMPANY_ID, 7L, new CompanyMemberRoleRequest(CompanyRole.OWNER), principal);
        controller.removeMember(COMPANY_ID, 7L, principal);

        verify(memberService).addMember(COMPANY_ID, 7L, CompanyRole.OWNER, ACTOR_ID, true);
        verify(memberService).changeRole(COMPANY_ID, 7L, CompanyRole.OWNER, ACTOR_ID, true);
        verify(memberService).removeMember(COMPANY_ID, 7L, ACTOR_ID, true);
        org.mockito.Mockito.verifyNoInteractions(permissionService);
    }

    @Test
    void platformAdminBypassesCompanyObjectPermission() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        org.mockito.Mockito.verifyNoInteractions(permissionService);
    }

    @Test
    void configuredPlatformAdminRoleBypassesCompanyObjectPermission() {
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<>(mockIdentityService()),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("COMPANY_ROOT"))));
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        org.mockito.Mockito.verifyNoInteractions(permissionService);
    }

    @Test
    void genericAdminDoesNotBypassWhenCustomPlatformAdminRoleIsConfigured() {
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<>(mockIdentityService()),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ADMIN"))));
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_READ);
    }

    @Test
    void strippedConfiguredPlatformAdminRoleDoesNotBypassCompanyObjectPermission() {
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<>(mockIdentityService()),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "ROLE_COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("COMPANY_ROOT"))));
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_READ);
    }

    @Test
    void prefixedAuthorityDoesNotBypassWhenConfiguredPlatformAdminRoleIsBare() {
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<>(mockIdentityService()),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("ROLE_COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ROOT"))));
        when(memberService.getMembers(COMPANY_ID, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.members(COMPANY_ID, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_READ);
    }

    @Test
    void objectLevelReadFailsClosedWhenIdentityServiceIsAbsent() {
        controller = new CompanyMgmtController(
                companyService,
                memberService,
                permissionService,
                new SingletonObjectProvider<IdentityService>(null),
                new SingletonObjectProvider<Environment>(new MockEnvironment()));
        when(companyService.get(COMPANY_ID)).thenReturn(company());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.get(COMPANY_ID, principal))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        org.mockito.Mockito.verifyNoInteractions(permissionService);
    }

    @Test
    void endpointAuthorizationUsesExistingCompanyPolicyContract() throws Exception {
        assertThat(preAuthorize("list", String.class, org.springframework.data.domain.Pageable.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin')");
        assertThat(preAuthorize("create", CompanyDto.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("get", Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')");
        assertThat(preAuthorize("update", Long.class, CompanyUpdateRequest.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("archive", Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("members", Long.class, UserDetails.class, org.springframework.data.domain.Pageable.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')");
        assertThat(preAuthorize("addMember", Long.class, CompanyMemberRequest.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("changeRole", Long.class, Long.class, studio.one.base.user.web.dto.CompanyMemberRoleRequest.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("removeMember", Long.class, Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("permissionsMe", Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')");
        assertThat(preAuthorize("permissionActions", Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')");
        assertThat(preAuthorize("permissionPolicy", Long.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','read')");
        assertThat(preAuthorize("updatePermissionPolicy", Long.class, CompanyPermissionPolicyUpdateRequest.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
    }

    private String preAuthorize(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = CompanyMgmtController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(PreAuthorize.class).value();
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(COMPANY_ID);
        company.setName("acme");
        company.setDisplayName("Acme");
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }

    private CompanyMemberRef member() {
        return new CompanyMemberRef(COMPANY_ID, 7L, CompanyRole.ADMIN, CompanyMemberStatus.ACTIVE, null, ACTOR_ID, null, ACTOR_ID);
    }

    private CompanyPermissionPolicyRef permissionPolicy(boolean override) {
        return new CompanyPermissionPolicyRef(COMPANY_ID, List.of(new CompanyPermissionRolePolicyRef(
                CompanyRole.ADMIN,
                List.of(CompanyPermissionActions.READ),
                List.of(CompanyPermissionActions.READ, CompanyPermissionActions.MEMBER_MANAGE),
                override)));
    }

    private IdentityService mockIdentityService() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(ACTOR_ID, "actor", Set.of())));
        return identityService;
    }

    private record SingletonObjectProvider<T>(T value) implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    }
}
