package studio.one.base.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyMember;
import studio.one.base.user.domain.model.ApplicationCompanyMemberId;
import studio.one.base.user.domain.error.CompanyJoinRequestException;
import studio.one.base.user.domain.port.ApplicationCompanyMemberRepository;
import studio.one.base.user.domain.port.ApplicationCompanyRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyMemberServiceImplTest {

    @Mock
    private ApplicationCompanyRepository companyRepository;

    @Mock
    private ApplicationCompanyMemberRepository memberRepository;

    @Test
    void addMemberDefaultsToMemberRole() {
        ApplicationCompany company = company();
        existingActorRole(CompanyRole.ADMIN);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(memberRepository.existsById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var ref = service().addMember(10L, 20L, null, 99L);

        assertThat(ref.companyId()).isEqualTo(10L);
        assertThat(ref.userId()).isEqualTo(20L);
        assertThat(ref.role()).isEqualTo(CompanyRole.MEMBER);
        assertThat(ref.joinedBy()).isEqualTo(99L);
    }

    @Test
    void addMemberRejectsExistingMember() {
        existingActorRole(CompanyRole.ADMIN);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberRepository.existsById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(true);

        assertThatThrownBy(() -> service().addMember(10L, 20L, CompanyRole.ADMIN, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.already-member");
    }

    @Test
    void addMemberRejectsRoleAboveActorRole() {
        existingActorRole(CompanyRole.ADMIN);

        assertThatThrownBy(() -> service().addMember(10L, 20L, CompanyRole.OWNER, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void changeRoleRejectsRoleAboveActorRole() {
        existingActorRole(CompanyRole.ADMIN);

        assertThatThrownBy(() -> service().changeRole(10L, 20L, CompanyRole.OWNER, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void changeRoleRejectsManagingTargetAboveActorRole() {
        existingActorRole(CompanyRole.ADMIN);
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().changeRole(10L, 20L, CompanyRole.MEMBER, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void removeMemberRejectsManagingTargetAboveActorRole() {
        existingActorRole(CompanyRole.ADMIN);
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service().removeMember(10L, 20L, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void changeRoleRejectsDemotingLastOwner() {
        existingActorRole(CompanyRole.OWNER);
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service().changeRole(10L, 20L, CompanyRole.ADMIN, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("last company owner");
    }

    @Test
    void removeMemberRejectsRemovingLastOwner() {
        existingActorRole(CompanyRole.OWNER);
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service().removeMember(10L, 20L, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("last company owner");
    }

    @Test
    void platformAdminBypassAllowsOwnerAssignment() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberRepository.existsById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(false);
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var ref = service().addMember(10L, 20L, CompanyRole.OWNER, 99L, true);

        assertThat(ref.role()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void platformAdminBypassAllowsOwnerRoleChange() {
        ApplicationCompanyMember target = member(20L, CompanyRole.ADMIN);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.save(target)).thenReturn(target);

        var ref = service().changeRole(10L, 20L, CompanyRole.OWNER, 99L, true);

        assertThat(ref.role()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void platformAdminBypassAllowsOwnerRemovalWhenAnotherOwnerExists() {
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).thenReturn(2L);

        service().removeMember(10L, 20L, 99L, true);

        org.mockito.Mockito.verify(memberRepository).deleteById(new ApplicationCompanyMemberId(10L, 20L));
    }

    @Test
    void platformAdminBypassCannotDemoteLastOwner() {
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service().changeRole(10L, 20L, CompanyRole.ADMIN, 99L, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("last company owner");
    }

    @Test
    void platformAdminBypassCannotRemoveLastOwner() {
        ApplicationCompanyMember target = member(20L, CompanyRole.OWNER);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(target));
        when(memberRepository.countByCompanyIdAndRole(10L, CompanyRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service().removeMember(10L, 20L, 99L, true))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("last company owner");
    }

    @Test
    void changeRoleUpdatesExistingMember() {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(10L, 20L));
        member.setRole(CompanyRole.MEMBER);
        existingActorRole(CompanyRole.ADMIN);
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);

        var ref = service().changeRole(10L, 20L, CompanyRole.ADMIN, 99L);

        assertThat(ref.role()).isEqualTo(CompanyRole.ADMIN);
        assertThat(ref.updatedBy()).isEqualTo(99L);
    }

    private ApplicationCompanyMemberServiceImpl service() {
        return new ApplicationCompanyMemberServiceImpl(companyRepository, memberRepository);
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(10L);
        company.setName("acme");
        company.setDisplayName("Acme");
        return company;
    }

    private void existingActorRole(CompanyRole role) {
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 99L))).thenReturn(Optional.of(member(99L, role)));
    }

    private ApplicationCompanyMember member(Long userId, CompanyRole role) {
        ApplicationCompanyMember actor = new ApplicationCompanyMember();
        actor.setId(new ApplicationCompanyMemberId(10L, userId));
        actor.setRole(role);
        return actor;
    }
}
