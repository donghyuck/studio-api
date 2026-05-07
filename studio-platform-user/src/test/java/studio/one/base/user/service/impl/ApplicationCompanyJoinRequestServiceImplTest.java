package studio.one.base.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.base.user.company.model.CompanyJoinRequestStatus;
import studio.one.base.user.company.model.CompanyMemberKeyStatus;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.model.CompanyStatus;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.domain.entity.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberKey;
import studio.one.base.user.exception.CompanyJoinRequestException;
import studio.one.base.user.persistence.ApplicationCompanyJoinRequestRepository;
import studio.one.base.user.persistence.ApplicationCompanyMemberKeyRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.service.ApplicationCompanyMemberService;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyJoinRequestServiceImplTest {

    @Mock
    private ApplicationCompanyRepository companyRepository;

    @Mock
    private ApplicationCompanyMemberKeyRepository memberKeyRepository;

    @Mock
    private ApplicationCompanyJoinRequestRepository joinRequestRepository;

    @Mock
    private ApplicationCompanyMemberService memberService;

    @Test
    void createMemberKeyReturnsPlainKeyOnceAndStoresHashOnly() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.ADMIN);
        when(memberKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ApplicationCompanyMemberKey> keyCaptor = ArgumentCaptor.forClass(ApplicationCompanyMemberKey.class);

        var ref = service().createMemberKey(10L, CompanyRole.ADMIN, null, 3, 99L);

        verify(memberKeyRepository).save(keyCaptor.capture());
        assertThat(ref.memberKey()).startsWith("cmk_");
        assertThat(keyCaptor.getValue().getKeyHash()).hasSize(64);
        assertThat(keyCaptor.getValue().getKeyHash()).isNotEqualTo(ref.memberKey());
        assertThat(ref.role()).isEqualTo(CompanyRole.ADMIN);
        assertThat(ref.maxUses()).isEqualTo(3);
    }

    @Test
    void createMemberKeyRejectsInactiveCompanyWithDomainException() {
        ApplicationCompany company = company();
        company.setStatus(CompanyStatus.ARCHIVED);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service().createMemberKey(10L, CompanyRole.MEMBER, null, null, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.company-inactive");

        verify(memberKeyRepository, never()).save(any());
    }

    @Test
    void createMemberKeyRejectsRoleAboveActorCompanyRole() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.ADMIN);

        assertThatThrownBy(() -> service().createMemberKey(10L, CompanyRole.OWNER, null, null, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(memberKeyRepository, never()).save(any());
    }

    @Test
    void createMemberKeyAllowsPlatformAdminBypass() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var ref = service().createMemberKey(10L, CompanyRole.OWNER, null, null, 99L, true);

        assertThat(ref.role()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void createRequestRejectsInvalidMemberKeyWithoutSavingRequest() {
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createSelfRequest("bad-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class);

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void createRequestRejectsExpiredMemberKey() {
        ApplicationCompanyMemberKey key = memberKey();
        key.setExpiresAt(Instant.now().minusSeconds(1));
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service().createSelfRequest("expired-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.member-key.expired");
    }

    @Test
    void createSelfRequestStoresPendingRequestWithoutConsumingKeyUse() {
        ApplicationCompanyMemberKey key = memberKey();
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(false);
        when(joinRequestRepository.existsPendingByCompanyIdAndUserId(10L, 7L)).thenReturn(false);
        when(joinRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var ref = service().createSelfRequest("valid-key", 7L, "User", "USER@example.com", "hello");

        assertThat(key.getUsedCount()).isZero();
        assertThat(ref.status()).isEqualTo(CompanyJoinRequestStatus.PENDING);
        assertThat(ref.email()).isEqualTo("user@example.com");
        assertThat(ref.requestedRole()).isEqualTo(CompanyRole.MEMBER);
        assertThat(ref.userId()).isEqualTo(7L);
    }

    @Test
    void createSelfRequestRejectsAlreadyExistingCompanyMemberBeforeSavingRequest() {
        ApplicationCompanyMemberKey key = memberKey();
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service().createSelfRequest("valid-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.already-member");

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void createSelfRequestRejectsInactiveMemberKey() {
        ApplicationCompanyMemberKey key = memberKey();
        key.setStatus(CompanyMemberKeyStatus.DISABLED);
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);

        assertThatThrownBy(() -> service().createSelfRequest("revoked-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.member-key.invalid");

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void createSelfRequestRejectsExhaustedMemberKeyIncludingPendingReservations() {
        ApplicationCompanyMemberKey key = memberKey();
        key.setMaxUses(1);
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service().createSelfRequest("full-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.member-key.exhausted");

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void createSelfRequestRejectsDuplicatePendingRequestForSameKeyAndUser() {
        ApplicationCompanyMemberKey key = memberKey();
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(false);
        when(joinRequestRepository.existsPendingByCompanyIdAndUserId(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service().createSelfRequest("valid-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.duplicate-pending");

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void listRequestsVerifiesCompanyExists() {
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(joinRequestRepository.findAllByCompanyId(10L, CompanyJoinRequestStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 10)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var page = service().getRequests(10L, CompanyJoinRequestStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        verify(companyRepository).findById(10L);
    }

    @Test
    void listRequestsRejectsUnknownCompany() {
        when(companyRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getRequests(10L, null, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(studio.one.platform.exception.NotFoundException.class);

        verify(joinRequestRepository, never()).findAllByCompanyId(any(), any(), any());
    }

    @Test
    void createSelfRequestMapsRaceDuplicateConstraintToDomainConflict() {
        ApplicationCompanyMemberKey key = memberKey();
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(false);
        when(joinRequestRepository.existsPendingByCompanyIdAndUserId(10L, 7L)).thenReturn(false);
        when(joinRequestRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate pending"));

        assertThatThrownBy(() -> service().createSelfRequest("valid-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.duplicate-pending");
    }

    @Test
    void approveAddsCompanyMemberAndMarksRequestApproved() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.ADMIN);
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(false);
        when(memberKeyRepository.save(request.getMemberKey())).thenReturn(request.getMemberKey());
        when(joinRequestRepository.save(request)).thenReturn(request);

        var ref = service().approve(10L, 123L, 99L);

        verify(memberService).addMember(10L, 7L, CompanyRole.MEMBER, 99L, true);
        assertThat(request.getMemberKey().getUsedCount()).isEqualTo(1);
        assertThat(ref.status()).isEqualTo(CompanyJoinRequestStatus.APPROVED);
        assertThat(ref.decidedBy()).isEqualTo(99L);
    }

    @Test
    void approveRejectsAlreadyExistingCompanyMember() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(true);

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.already-member");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    @Test
    void approveRejectsRequestedRoleAboveActorCompanyRole() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        request.setRequestedRole(CompanyRole.OWNER);
        request.getMemberKey().setRole(CompanyRole.OWNER);
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));
        when(memberService.isCompanyMember(10L, 7L)).thenReturn(false);
        when(memberService.getCompanyRole(10L, 99L)).thenReturn(CompanyRole.ADMIN);

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(memberService, never()).addMember(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void createSelfRequestRejectsInactiveCompany() {
        ApplicationCompany inactive = company();
        inactive.setStatus(CompanyStatus.ARCHIVED);
        ApplicationCompanyMemberKey key = memberKey();
        when(memberKeyRepository.findForUpdateByKeyHash(any())).thenReturn(Optional.of(key));
        when(joinRequestRepository.countPendingByKeyId(1L)).thenReturn(0L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service().createSelfRequest("valid-key", 7L, "User", "user@example.com", null))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.company-inactive");

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void approveRejectsInactiveCompany() {
        ApplicationCompany inactive = company();
        inactive.setStatus(CompanyStatus.ARCHIVED);
        ApplicationCompanyJoinRequest request = pendingRequest();
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.company-inactive");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    @Test
    void approveRejectsInactiveMemberKey() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        request.getMemberKey().setStatus(CompanyMemberKeyStatus.DISABLED);
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.member-key.invalid");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    @Test
    void approveRejectsExhaustedMemberKey() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        request.getMemberKey().setMaxUses(1);
        request.getMemberKey().setUsedCount(1);
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.member-key.exhausted");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    @Test
    void alreadyDecidedRequestCannotBeApprovedAgain() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        request.setStatus(CompanyJoinRequestStatus.APPROVED);
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.already-decided");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    @Test
    void rejectUsesLockedPendingRequestPath() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(joinRequestRepository.save(request)).thenReturn(request);

        var ref = service().reject(10L, 123L, 99L);

        assertThat(ref.status()).isEqualTo(CompanyJoinRequestStatus.REJECTED);
        verify(joinRequestRepository).findForUpdateById(123L);
    }

    @Test
    void anonymousRequestWithoutAuthenticatedUserCannotBeApprovedByEmailOnly() {
        ApplicationCompanyJoinRequest request = pendingRequest();
        request.setUserId(null);
        request.setEmail("user@example.com");
        when(joinRequestRepository.findForUpdateById(123L)).thenReturn(Optional.of(request));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company()));
        when(memberKeyRepository.findForUpdateById(1L)).thenReturn(Optional.of(request.getMemberKey()));

        assertThatThrownBy(() -> service().approve(10L, 123L, 99L))
                .isInstanceOf(CompanyJoinRequestException.class)
                .hasMessageContaining("error.company.join-request.user-required");

        verify(memberService, never()).addMember(any(), any(), any(), any());
    }

    private ApplicationCompanyJoinRequestServiceImpl service() {
        return new ApplicationCompanyJoinRequestServiceImpl(
                companyRepository,
                memberKeyRepository,
                joinRequestRepository,
                memberService);
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(10L);
        company.setName("acme");
        company.setDisplayName("Acme");
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }

    private ApplicationCompanyMemberKey memberKey() {
        ApplicationCompanyMemberKey key = new ApplicationCompanyMemberKey();
        key.setKeyId(1L);
        key.setCompany(company());
        key.setCompanyId(10L);
        key.setRole(CompanyRole.MEMBER);
        key.setKeyHash("hash");
        key.setStatus(CompanyMemberKeyStatus.ACTIVE);
        return key;
    }

    private ApplicationCompanyJoinRequest pendingRequest() {
        ApplicationCompanyJoinRequest request = new ApplicationCompanyJoinRequest();
        request.setRequestId(123L);
        request.setCompany(company());
        request.setCompanyId(10L);
        request.setMemberKey(memberKey());
        request.setKeyId(1L);
        request.setUserId(7L);
        request.setRequestedRole(CompanyRole.MEMBER);
        request.setStatus(CompanyJoinRequestStatus.PENDING);
        return request;
    }
}
