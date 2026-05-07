package studio.one.base.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import studio.one.base.user.company.model.CompanyPermissionRolePolicyRef;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicyId;
import studio.one.base.user.persistence.ApplicationCompanyPermissionPolicyRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.service.ApplicationCompanyMemberService;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyPermissionServiceImplTest {

    @Mock
    private ApplicationCompanyMemberService memberService;

    @Mock
    private ApplicationCompanyRepository companyRepository;

    @Mock
    private ApplicationCompanyPermissionPolicyRepository policyRepository;

    @Test
    void ownerGetsArchiveAndWorkspaceCreatePermissions() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.OWNER);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        ApplicationCompanyPermissionServiceImpl service = service();

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.ARCHIVE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.WORKSPACE_CREATE)).isTrue();
    }

    @Test
    void billingAdminDoesNotManageMembers() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.BILLING_ADMIN);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        ApplicationCompanyPermissionServiceImpl service = service();

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.BILLING_MANAGE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.MEMBER_MANAGE)).isFalse();
    }

    @Test
    void adminDoesNotGetBillingPermissions() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.ADMIN);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        ApplicationCompanyPermissionServiceImpl service = service();

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.MEMBER_MANAGE)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.BILLING_MANAGE)).isFalse();
    }

    @Test
    void assertGrantedThrowsAccessDenied() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.MEMBER);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        ApplicationCompanyPermissionServiceImpl service = service();

        assertThatThrownBy(() -> service.assertGranted(10L, 20L, CompanyPermissionActions.ARCHIVE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void storedPolicyOverridesDefaultRoleMapping() {
        when(memberService.getCompanyRole(10L, 20L)).thenReturn(CompanyRole.ADMIN);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of(
                policy(CompanyRole.ADMIN, CompanyPermissionActions.READ, true),
                policy(CompanyRole.ADMIN, CompanyPermissionActions.MEMBER_MANAGE, false)));

        ApplicationCompanyPermissionServiceImpl service = service();

        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.READ)).isTrue();
        assertThat(service.isGranted(10L, 20L, CompanyPermissionActions.MEMBER_MANAGE)).isFalse();
        assertThat(service.getGrantedActions(10L, 20L)).containsExactly(CompanyPermissionActions.READ);
    }

    @Test
    void getPolicyMarksDefaultAndOverrideRoles() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of(
                policy(CompanyRole.ADMIN, CompanyPermissionActions.READ, true),
                policy(CompanyRole.ADMIN, CompanyPermissionActions.MEMBER_MANAGE, false)));

        var policy = service().getPolicy(10L);

        assertThat(policy.companyId()).isEqualTo(10L);
        assertThat(policy.roles()).filteredOn(role -> role.role() == CompanyRole.ADMIN).singleElement()
                .satisfies(role -> {
                    assertThat(role.override()).isTrue();
                    assertThat(role.actions()).containsExactly(CompanyPermissionActions.READ);
                    assertThat(role.defaultActions()).contains(CompanyPermissionActions.MEMBER_MANAGE);
                });
        assertThat(policy.roles()).filteredOn(role -> role.role() == CompanyRole.MEMBER).singleElement()
                .satisfies(role -> assertThat(role.override()).isFalse());
    }

    @Test
    void updatePolicyValidatesUnknownActions() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));

        assertThatThrownBy(() -> service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(CompanyRole.ADMIN, List.of("company.unknown"), List.of(), true)),
                99L,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown company permission action");
    }

    @Test
    void updatePolicyRejectsBlankActions() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));

        assertThatThrownBy(() -> service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(CompanyRole.ADMIN, Collections.singletonList(null), List.of(), true)),
                99L,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action must not be blank");

        assertThatThrownBy(() -> service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(CompanyRole.ADMIN, List.of(" "), List.of(), true)),
                99L,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("action must not be blank");
    }

    @Test
    void updatePolicyStoresAllDefinitionsForProvidedRoles() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.OWNER);
        when(policyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        List.of(),
                        true)),
                99L,
                false);

        org.mockito.Mockito.verify(policyRepository).deleteAllByCompanyId(10L);
        org.mockito.Mockito.verify(policyRepository, org.mockito.Mockito.times(CompanyPermissionActions.definitions().size()))
                .save(any());
    }

    @Test
    void updatePolicySkipsRolesMarkedAsDefault() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.OWNER);
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(
                        CompanyRole.ADMIN,
                        sorted(CompanyPermissionActions.actionsFor(CompanyRole.ADMIN)),
                        sorted(CompanyPermissionActions.actionsFor(CompanyRole.ADMIN)),
                        false)),
                99L,
                false);

        org.mockito.Mockito.verify(policyRepository).deleteAllByCompanyId(10L);
        org.mockito.Mockito.verify(policyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updatePolicyRequiresCompanyOwnerWhenNotPlatformAdmin() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.ADMIN);

        assertThatThrownBy(() -> service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        List.of(),
                        true)),
                99L,
                false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updatePolicyAllowsPlatformAdminWithoutCompanyOwnerRole() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(policyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(policyRepository.findAllByCompanyId(10L)).thenReturn(List.of());

        service().updatePolicy(
                10L,
                List.of(new CompanyPermissionRolePolicyRef(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        List.of(),
                        true)),
                99L,
                true);

        org.mockito.Mockito.verify(memberService, org.mockito.Mockito.never()).getCompanyRole(10L, 99L);
        org.mockito.Mockito.verify(policyRepository).deleteAllByCompanyId(10L);
        org.mockito.Mockito.verify(policyRepository, org.mockito.Mockito.times(CompanyPermissionActions.definitions().size()))
                .save(any());
    }

    @Test
    void updatePolicyRejectsInvalidCompanyIdBeforePlatformAdminBypass() {
        assertThatThrownBy(() -> service().updatePolicy(
                0L,
                List.of(new CompanyPermissionRolePolicyRef(
                        CompanyRole.ADMIN,
                        List.of(CompanyPermissionActions.READ),
                        List.of(),
                        true)),
                99L,
                true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId must be positive");

        org.mockito.Mockito.verifyNoInteractions(companyRepository, memberService, policyRepository);
    }

    private ApplicationCompanyPermissionServiceImpl service() {
        return new ApplicationCompanyPermissionServiceImpl(companyRepository, memberService, policyRepository);
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(10L);
        company.setName("acme");
        company.setDisplayName("Acme");
        return company;
    }

    private ApplicationCompanyPermissionPolicy policy(CompanyRole role, String action, boolean enabled) {
        ApplicationCompanyPermissionPolicy policy = new ApplicationCompanyPermissionPolicy();
        policy.setId(new ApplicationCompanyPermissionPolicyId(10L, role, action));
        policy.setEnabled(enabled);
        return policy;
    }

    private List<String> sorted(java.util.Set<String> actions) {
        return actions.stream().sorted().toList();
    }
}
