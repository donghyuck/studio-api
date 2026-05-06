package studio.one.base.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.service.ApplicationCompanyMemberService;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyPermissionServiceImplTest {

    @Mock
    private ApplicationCompanyMemberService memberService;

    @Test
    void ownerGetsArchiveAndWorkspaceCreatePermissions() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.OWNER);

        ApplicationCompanyPermissionServiceImpl service = new ApplicationCompanyPermissionServiceImpl(memberService);

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.ARCHIVE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.WORKSPACE_CREATE)).isTrue();
    }

    @Test
    void billingAdminDoesNotManageMembers() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.BILLING_ADMIN);

        ApplicationCompanyPermissionServiceImpl service = new ApplicationCompanyPermissionServiceImpl(memberService);

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.BILLING_MANAGE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.MEMBER_MANAGE)).isFalse();
    }

    @Test
    void adminDoesNotGetBillingPermissions() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.ADMIN);

        ApplicationCompanyPermissionServiceImpl service = new ApplicationCompanyPermissionServiceImpl(memberService);

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.MEMBER_MANAGE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.BILLING_MANAGE)).isFalse();
    }

    @Test
    void assertGrantedThrowsAccessDenied() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.MEMBER);

        ApplicationCompanyPermissionServiceImpl service = new ApplicationCompanyPermissionServiceImpl(memberService);

        assertThatThrownBy(() -> service.assertGranted(10L, 20L, CompanyPermissionActions.ARCHIVE))
                .isInstanceOf(AccessDeniedException.class);
    }
}
