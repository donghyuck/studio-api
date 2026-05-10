package studio.one.base.user.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyRef;
import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.web.dto.request.CompanyMemberKeyCreateRequest;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

class CompanyJoinRequestMgmtControllerTest {

    private static final Long COMPANY_ID = 10L;
    private static final Long ACTOR_ID = 99L;

    private ApplicationCompanyPermissionService permissionService;
    private ApplicationCompanyJoinRequestService joinRequestService;
    private CompanyJoinRequestMgmtController controller;
    private UserDetails principal;

    @BeforeEach
    void setUp() {
        permissionService = mock(ApplicationCompanyPermissionService.class);
        joinRequestService = mock(ApplicationCompanyJoinRequestService.class);
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(ACTOR_ID, "actor", Set.of())));
        controller = new CompanyJoinRequestMgmtController(
                permissionService,
                joinRequestService,
                identityService,
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
    void memberKeyCreationRequiresCompanyMemberManagePermission() {
        when(joinRequestService.createMemberKey(COMPANY_ID, CompanyRole.MEMBER, null, null, ACTOR_ID, false))
                .thenReturn(memberKey());

        controller.createMemberKey(COMPANY_ID, new CompanyMemberKeyCreateRequest(CompanyRole.MEMBER, null, null), principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
        verify(joinRequestService).createMemberKey(COMPANY_ID, CompanyRole.MEMBER, null, null, ACTOR_ID, false);
    }

    @Test
    void memberJoinRequestListRequiresCompanyMemberManagePermission() {
        when(joinRequestService.getRequests(COMPANY_ID, null, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.memberJoinRequests(COMPANY_ID, null, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void memberJoinRequestApprovalRequiresCompanyMemberManagePermission() {
        when(joinRequestService.approve(COMPANY_ID, 123L, ACTOR_ID, false)).thenReturn(joinRequest());

        controller.approveMemberJoinRequest(COMPANY_ID, 123L, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
        verify(joinRequestService).approve(COMPANY_ID, 123L, ACTOR_ID, false);
    }

    @Test
    void memberJoinRequestRejectionRequiresCompanyMemberManagePermission() {
        when(joinRequestService.reject(COMPANY_ID, 123L, ACTOR_ID)).thenReturn(joinRequest());

        controller.rejectMemberJoinRequest(COMPANY_ID, 123L, principal);

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void platformAdminBypassesCompanyObjectPermission() {
        controller = new CompanyJoinRequestMgmtController(
                permissionService,
                joinRequestService,
                mockIdentityService(),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("COMPANY_ROOT"))));
        when(joinRequestService.getRequests(COMPANY_ID, null, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.memberJoinRequests(COMPANY_ID, null, principal, PageRequest.of(0, 15));

        org.mockito.Mockito.verifyNoInteractions(permissionService);
    }

    @Test
    void prefixedAuthorityDoesNotBypassWhenConfiguredPlatformAdminRoleIsBare() {
        controller = new CompanyJoinRequestMgmtController(
                permissionService,
                joinRequestService,
                mockIdentityService(),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("ROLE_COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_COMPANY_ROOT"))));
        when(joinRequestService.getRequests(COMPANY_ID, null, PageRequest.of(0, 15))).thenReturn(Page.empty());

        controller.memberJoinRequests(COMPANY_ID, null, principal, PageRequest.of(0, 15));

        verify(permissionService).assertGranted(COMPANY_ID, ACTOR_ID, CompanyPermissionActions.MEMBER_MANAGE);
    }

    @Test
    void platformAdminApprovalUsesRoleLimitBypass() {
        controller = new CompanyJoinRequestMgmtController(
                permissionService,
                joinRequestService,
                mockIdentityService(),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("COMPANY_ROOT"))));
        when(joinRequestService.approve(COMPANY_ID, 123L, ACTOR_ID, true)).thenReturn(joinRequest());

        controller.approveMemberJoinRequest(COMPANY_ID, 123L, principal);

        org.mockito.Mockito.verifyNoInteractions(permissionService);
        verify(joinRequestService).approve(COMPANY_ID, 123L, ACTOR_ID, true);
    }

    @Test
    void platformAdminMemberKeyCreationUsesRoleLimitBypass() {
        controller = new CompanyJoinRequestMgmtController(
                permissionService,
                joinRequestService,
                mockIdentityService(),
                new SingletonObjectProvider<Environment>(new MockEnvironment()
                        .withProperty("studio.security.acl.admin-role", "COMPANY_ROOT")));
        principal = User.withUsername("actor").password("n/a").authorities("COMPANY_ROOT").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("COMPANY_ROOT"))));
        when(joinRequestService.createMemberKey(COMPANY_ID, CompanyRole.OWNER, null, null, ACTOR_ID, true))
                .thenReturn(new CompanyMemberKeyRef(1L, COMPANY_ID, CompanyRole.OWNER, "cmk_test", CompanyMemberKeyStatus.ACTIVE, null, null, 0, null, ACTOR_ID));

        controller.createMemberKey(COMPANY_ID, new CompanyMemberKeyCreateRequest(CompanyRole.OWNER, null, null), principal);

        org.mockito.Mockito.verifyNoInteractions(permissionService);
        verify(joinRequestService).createMemberKey(COMPANY_ID, CompanyRole.OWNER, null, null, ACTOR_ID, true);
    }

    @Test
    void endpointAuthorizationUsesCompanyPolicyContract() throws Exception {
        assertThat(preAuthorize("createMemberKey", Long.class, CompanyMemberKeyCreateRequest.class, UserDetails.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
        assertThat(preAuthorize("memberJoinRequests", Long.class, CompanyJoinRequestStatus.class, UserDetails.class, org.springframework.data.domain.Pageable.class))
                .isEqualTo("@endpointAuthz.can('features:company','admin') or @endpointAuthz.can('features:company','write')");
    }

    @Test
    void memberKeyValidationRejectsPastExpiration() throws Exception {
        IdentityService identityService = mockIdentityService();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanyJoinRequestMgmtController(
                        permissionService,
                        joinRequestService,
                        identityService,
                        new SingletonObjectProvider<Environment>(null)))
                .setValidator(validator())
                .build();

        mvc.perform(post("/api/mgmt/companies/{companyId}/member-keys", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s","maxUses":1}
                                """.formatted(Instant.now().minusSeconds(60))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberKeyCreationResponseDisablesCachingForPlainSecret() throws Exception {
        when(joinRequestService.createMemberKey(COMPANY_ID, CompanyRole.MEMBER, null, null, ACTOR_ID, false))
                .thenReturn(memberKey());

        var response = controller.createMemberKey(
                COMPANY_ID,
                new CompanyMemberKeyCreateRequest(CompanyRole.MEMBER, null, null),
                principal);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void memberKeyValidationRejectsNonPositiveMaxUses() throws Exception {
        IdentityService identityService = mockIdentityService();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanyJoinRequestMgmtController(
                        permissionService,
                        joinRequestService,
                        identityService,
                        new SingletonObjectProvider<Environment>(null)))
                .setValidator(validator())
                .build();

        mvc.perform(post("/api/mgmt/companies/{companyId}/member-keys", COMPANY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","maxUses":0}
                                """))
                .andExpect(status().isBadRequest());
    }

    private String preAuthorize(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = CompanyJoinRequestMgmtController.class.getMethod(methodName, parameterTypes);
        return method.getAnnotation(PreAuthorize.class).value();
    }

    private CompanyMemberKeyRef memberKey() {
        return new CompanyMemberKeyRef(1L, COMPANY_ID, CompanyRole.MEMBER, "cmk_test", CompanyMemberKeyStatus.ACTIVE, null, null, 0, null, ACTOR_ID);
    }

    private CompanyJoinRequestRef joinRequest() {
        return new CompanyJoinRequestRef(123L, COMPANY_ID, 1L, 7L, "user", "user@example.com", null, CompanyRole.MEMBER, CompanyJoinRequestStatus.PENDING, null, null, null, null);
    }

    private IdentityService mockIdentityService() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(ACTOR_ID, "actor", Set.of())));
        return identityService;
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
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
