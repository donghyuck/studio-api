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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import studio.one.base.user.domain.model.Role;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.service.BatchResult;
import studio.one.base.user.service.PasswordPolicyService;
import studio.one.base.user.web.dto.ChangePasswordRequest;
import studio.one.base.user.web.dto.RoleDto;
import studio.one.base.user.web.mapper.ApplicationRoleMapper;
import studio.one.base.user.web.mapper.ApplicationUserMapper;

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

    @Test
    void passwordResetRejectsMissingActor() {
        UserMgmtController controller = controller();

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.passwordReset(10L, changePasswordRequest("NextPassword123!"), null));
    }

    @Test
    void updateUserRolesRejectsMissingActor() {
        UserMgmtController controller = controller();

        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.updateUserRoles(10L, List.of(RoleDto.builder().roleId(1L).build()), null));
    }

    @Test
    void findReturnsEmptyPageWithoutServiceCallWhenQueryRequired() {
        UserMgmtController controller = controller();

        Page<?> result = controller.find(Optional.empty(), true, Pageable.unpaged()).getBody().getData();

        assertEquals(0, result.getTotalElements());
        verify(userService, never()).findAll(any());
        verify(userService, never()).findByNameOrUsernameOrEmail(any(), any());
    }

    @Test
    void passwordResetDelegatesActorUsername() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("admin").password("n/a").authorities("ROLE_ADMIN").build();

        controller.passwordReset(10L, changePasswordRequest("NextPassword123!"), actor);

        verify(userService).resetPassword(eq(10L), eq("NextPassword123!"), eq("admin"), eq("test-reason"));
    }

    @Test
    void updateUserRolesDelegatesDistinctRoleIds() {
        UserMgmtController controller = controller();
        UserDetails actor = User.withUsername("admin").password("n/a").authorities("ROLE_ADMIN").build();
        RoleDto role1 = RoleDto.builder().roleId(1L).build();
        RoleDto role2 = RoleDto.builder().roleId(1L).build();
        RoleDto role3 = RoleDto.builder().roleId(2L).build();
        when(userService.updateUserRolesBulk(10L, List.of(1L, 2L), "admin")).thenReturn(new BatchResult(2, 2, 0, 0));

        controller.updateUserRoles(10L, List.of(role1, role2, role3), actor);

        verify(userService).updateUserRolesBulk(10L, List.of(1L, 2L), "admin");
    }

    private UserMgmtController controller() {
        return new UserMgmtController(userService, userMapper, roleMapper, passwordPolicyService);
    }

    private ChangePasswordRequest changePasswordRequest(String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setNewPassword(newPassword);
        request.setReason("test-reason");
        return request;
    }
}
