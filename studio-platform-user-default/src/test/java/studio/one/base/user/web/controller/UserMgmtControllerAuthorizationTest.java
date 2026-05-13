package studio.one.base.user.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationUserService;
import studio.one.base.user.application.result.BatchResult;
import studio.one.base.user.application.usecase.PasswordPolicyService;
import studio.one.base.user.web.dto.request.ChangePasswordRequest;
import studio.one.base.user.web.dto.response.RoleDto;
import studio.one.base.user.web.dto.request.UpdateRolesRequest;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

@ExtendWith(MockitoExtension.class)
class UserMgmtControllerAuthorizationTest {

    @Mock
    private ApplicationUserService<studio.one.base.user.domain.model.User, Role> userService;

    @Mock
    private ApplicationUserMapper userMapper;

    @Mock
    private ApplicationRoleMapper roleMapper;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private ObjectProvider<ApplicationCompanyPermissionService> companyPermissionServiceProvider;

    @Mock
    private ApplicationCompanyPermissionService companyPermissionService;

    @Mock
    private ObjectProvider<IdentityService> identityServiceProvider;

    @Mock
    private IdentityService identityService;

    @Mock
    private ObjectProvider<Environment> environmentProvider;

    @Test
    void passwordResetRejectsMissingActor() {
        UserMgmtController controller = controller();

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.passwordReset(10L, changePasswordRequest("NextPassword123!"), null));
    }

    @Test
    void updateUserRolesRejectsMissingActor() {
        UserMgmtController controller = controller();

        UpdateRolesRequest req = new UpdateRolesRequest();
        req.setRoleIds(List.of(1L));
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.updateUserRoles(10L, req, null));
    }

    @Test
    void findReturnsEmptyPageWithoutServiceCallWhenQueryRequired() {
        UserMgmtController controller = controller();

        Page<?> result = controller.find(Optional.empty(), Optional.empty(), true, null, Pageable.unpaged()).getBody().getData();

        assertEquals(0, result.getTotalElements());
        verify(userService, never()).findAll(any());
        verify(userService, never()).findByNameOrUsernameOrEmail(any(), any());
    }

    @Test
    void listDelegatesCompanyAndKeywordFilter() {
        UserMgmtController controller = controller();
        Pageable pageable = Pageable.unpaged();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(companyPermissionService);
        when(identityServiceProvider.getIfAvailable()).thenReturn(identityService);
        when(identityService.findByUsername("manager")).thenReturn(Optional.of(new UserRef(99L, "manager", java.util.Set.of())));
        when(userService.findByCompanyIdAndNameOrUsernameOrEmail(20L, "kim", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        controller.list(Optional.of(" kim "), Optional.of(20L), actor, pageable);

        verify(companyPermissionService).assertGranted(20L, 99L, CompanyPermissionActions.MEMBER_READ);
        verify(userService).findByCompanyIdAndNameOrUsernameOrEmail(20L, "kim", pageable);
        verify(userService, never()).findByNameOrUsernameOrEmail(any(), any());
        verify(userService, never()).findAll(any());
    }

    @Test
    void findAllowsCompanyFilterWhenQueryIsRequired() {
        UserMgmtController controller = controller();
        Pageable pageable = Pageable.unpaged();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(companyPermissionService);
        when(identityServiceProvider.getIfAvailable()).thenReturn(identityService);
        when(identityService.findByUsername("manager")).thenReturn(Optional.of(new UserRef(99L, "manager", java.util.Set.of())));
        when(userService.findAllByCompanyId(20L, pageable)).thenReturn(new PageImpl<>(List.of()));

        controller.find(Optional.empty(), Optional.of(20L), true, actor, pageable);

        verify(userService).findAllByCompanyId(20L, pageable);
    }

    @Test
    void companyFilterMapsUnsupportedServiceCapabilityToNotImplemented() {
        UserMgmtController controller = controller();
        Pageable pageable = Pageable.unpaged();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(companyPermissionService);
        when(identityServiceProvider.getIfAvailable()).thenReturn(identityService);
        when(identityService.findByUsername("manager")).thenReturn(Optional.of(new UserRef(99L, "manager", java.util.Set.of())));
        when(userService.findAllByCompanyId(20L, pageable)).thenThrow(new UnsupportedOperationException("not supported"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.list(Optional.empty(), Optional.of(20L), actor, pageable));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.getStatus());
    }

    @Test
    void companyFilterWithoutPermissionServiceReturnsNotImplemented() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.list(Optional.empty(), Optional.of(20L), actor, Pageable.unpaged()));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.getStatus());
        verify(userService, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void companyFilterWithoutIdentityServiceReturnsNotImplemented() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(companyPermissionService);
        when(identityServiceProvider.getIfAvailable()).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.list(Optional.empty(), Optional.of(20L), actor, Pageable.unpaged()));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, exception.getStatus());
        verify(userService, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void companyFilterWithoutResolvedActorReturnsUnauthorized() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("manager").password("n/a").authorities("ROLE_USER").build();
        when(companyPermissionServiceProvider.getIfAvailable()).thenReturn(companyPermissionService);
        when(identityServiceProvider.getIfAvailable()).thenReturn(identityService);
        when(identityService.findByUsername("manager")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.list(Optional.empty(), Optional.of(20L), actor, Pageable.unpaged()));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(userService, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void companyFilterRejectsInvalidCompanyIdAsBadRequest() {
        UserMgmtController controller = controller();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.list(Optional.empty(), Optional.of(0L), null, Pageable.unpaged()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(userService, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void companyFilterPlatformAdminBypassesCompanyPermissionCheck() {
        UserMgmtController controller = controller();
        Pageable pageable = Pageable.unpaged();
        UserDetails actor = User.withUsername("admin").password("n/a").authorities("ROLE_ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(actor, "n/a", "ROLE_ADMIN"));
        when(environmentProvider.getIfAvailable()).thenReturn(null);
        when(userService.findAllByCompanyId(20L, pageable)).thenReturn(new PageImpl<>(List.of()));

        try {
            controller.list(Optional.empty(), Optional.of(20L), actor, pageable);
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(companyPermissionServiceProvider, never()).getIfAvailable();
        verify(userService).findAllByCompanyId(20L, pageable);
    }

    @Test
    void passwordResetDelegatesActorUsername() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("admin").password("n/a").authorities("ROLE_ADMIN").build();

        controller.passwordReset(10L, changePasswordRequest("NextPassword123!"), actor);

        verify(userService).resetPassword(eq(10L), eq("NextPassword123!"), eq("admin"), eq("test-reason"));
    }

    @Test
    void deleteDelegatesToUserService() {
        UserMgmtController controller = controller();

        var response = controller.delete(10L);

        assertEquals(204, response.getStatusCodeValue());
        verify(userService).delete(10L);
    }

    @Test
    void updateUserRolesDelegatesDistinctRoleIds() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("admin").password("n/a").authorities("ROLE_ADMIN").build();
        UpdateRolesRequest req = new UpdateRolesRequest();
        req.setRoleIds(List.of(1L, 1L, 2L));
        when(userService.updateUserRolesBulk(10L, List.of(1L, 2L), "admin")).thenReturn(new BatchResult(2, 2, 0, 0));

        controller.updateUserRoles(10L, req, actor);

        verify(userService).updateUserRolesBulk(10L, List.of(1L, 2L), "admin");
    }

    private UserMgmtController controller() {
        return new UserMgmtController(
                userService,
                userMapper,
                roleMapper,
                passwordPolicyService,
                companyPermissionServiceProvider,
                identityServiceProvider,
                environmentProvider);
    }

    private ChangePasswordRequest changePasswordRequest(String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setNewPassword(newPassword);
        request.setReason("test-reason");
        return request;
    }
}
