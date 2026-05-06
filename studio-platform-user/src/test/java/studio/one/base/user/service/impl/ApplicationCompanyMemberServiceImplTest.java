package studio.one.base.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.domain.entity.ApplicationCompanyMember;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberId;
import studio.one.base.user.persistence.ApplicationCompanyMemberRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyMemberServiceImplTest {

    @Mock
    private ApplicationCompanyRepository companyRepository;

    @Mock
    private ApplicationCompanyMemberRepository memberRepository;

    @Test
    void addMemberDefaultsToMemberRole() {
        ApplicationCompany company = company();
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(memberRepository.findById(new ApplicationCompanyMemberId(10L, 20L))).thenReturn(Optional.empty());
        when(memberRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var ref = service().addMember(10L, 20L, null, 99L);

        assertThat(ref.companyId()).isEqualTo(10L);
        assertThat(ref.userId()).isEqualTo(20L);
        assertThat(ref.role()).isEqualTo(CompanyRole.MEMBER);
        assertThat(ref.joinedBy()).isEqualTo(99L);
    }

    @Test
    void changeRoleUpdatesExistingMember() {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(10L, 20L));
        member.setRole(CompanyRole.MEMBER);
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
}
