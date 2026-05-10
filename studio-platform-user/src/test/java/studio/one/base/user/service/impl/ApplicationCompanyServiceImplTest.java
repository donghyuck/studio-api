package studio.one.base.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.port.ApplicationCompanyRepository;
import studio.one.base.user.infrastructure.persistence.jpa.ApplicationCompanyJpaRepository;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.platform.service.I18n;

@ExtendWith(MockitoExtension.class)
class ApplicationCompanyServiceImplTest {

    @Mock
    private ApplicationCompanyRepository companyRepository;

    @Mock
    private ObjectProvider<ApplicationCompanyMemberService> memberServiceProvider;

    @Mock
    private ApplicationCompanyMemberService memberService;

    @Mock
    private ObjectProvider<I18n> i18nProvider;

    @Test
    void createWithActorRegistersOwnerMember() {
        ApplicationCompany company = company();
        ApplicationCompany saved = company();
        saved.setCompanyId(10L);
        when(companyRepository.save(company)).thenReturn(saved);
        when(memberServiceProvider.getIfAvailable()).thenReturn(memberService);

        ApplicationCompany result = service().create(company, 99L);

        assertThat(result.getCompanyId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        verify(memberService).addMember(10L, 99L, CompanyRole.OWNER, 99L);
    }

    @Test
    void createWithoutActorKeepsLegacyBehavior() {
        ApplicationCompany company = company();
        ApplicationCompany saved = company();
        saved.setCompanyId(10L);
        when(companyRepository.save(company)).thenReturn(saved);

        service().create(company);

        verify(memberServiceProvider, never()).getIfAvailable();
    }

    @Test
    void createWithActorRequiresMemberService() {
        ApplicationCompany company = company();
        ApplicationCompany saved = company();
        saved.setCompanyId(10L);
        when(companyRepository.save(company)).thenReturn(saved);
        when(memberServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service().create(company, 99L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void archiveMarksCompanyArchived() {
        ApplicationCompany company = company();
        company.setCompanyId(10L);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.save(company)).thenReturn(company);

        ApplicationCompany archived = service().archive(10L, 99L);

        assertThat(archived.getStatus()).isEqualTo(CompanyStatus.ARCHIVED);
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getArchivedBy()).isEqualTo(99L);
    }

    @Test
    void deleteTranslatesDependentDataConstraintViolation() {
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(companyRepository)
                .deleteById(10L);

        assertThatThrownBy(() -> service().delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archive the company");
    }

    @Test
    void deleteTranslatesJpaFlushConstraintViolation() {
        ApplicationCompanyJpaRepository jpaRepository = org.mockito.Mockito.mock(ApplicationCompanyJpaRepository.class);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fk"))
                .when(jpaRepository)
                .flush();

        assertThatThrownBy(() -> new ApplicationCompanyServiceImpl(jpaRepository, memberServiceProvider, i18nProvider)
                .delete(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archive the company");
    }

    private ApplicationCompanyServiceImpl service() {
        return new ApplicationCompanyServiceImpl(companyRepository, memberServiceProvider, i18nProvider);
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setName("acme");
        company.setDisplayName("Acme");
        return company;
    }
}
