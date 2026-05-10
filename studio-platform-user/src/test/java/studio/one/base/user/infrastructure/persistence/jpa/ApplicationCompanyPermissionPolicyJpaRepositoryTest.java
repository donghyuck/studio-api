package studio.one.base.user.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.model.ApplicationCompanyPermissionPolicyId;
import studio.one.base.user.domain.port.ApplicationCompanyPermissionPolicyRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationCompanyPermissionPolicyJpaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager em;

    @Autowired
    ApplicationCompanyPermissionPolicyJpaRepository repository;

    @Test
    void storesListsAndDeletesPoliciesByCompany() {
        ApplicationCompany company = company();
        em.persist(company);
        ApplicationCompanyPermissionPolicyRepository policyRepository = repository;
        policyRepository.save(policy(company, CompanyRole.ADMIN, CompanyPermissionActions.READ, true));
        policyRepository.save(policy(company, CompanyRole.ADMIN, CompanyPermissionActions.MEMBER_MANAGE, false));
        policyRepository.save(policy(company, CompanyRole.OWNER, CompanyPermissionActions.ARCHIVE, true));
        em.flush();
        em.clear();

        var policies = repository.findAllByCompanyId(company.getCompanyId());

        assertThat(policies).hasSize(3);
        assertThat(policies).extracting(policy -> policy.getId().getRole()).contains(CompanyRole.ADMIN, CompanyRole.OWNER);

        repository.deleteAllByCompanyId(company.getCompanyId());
        em.flush();

        assertThat(repository.findAllByCompanyId(company.getCompanyId())).isEmpty();
    }

    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setName("policy-jpa");
        company.setDisplayName("Policy JPA");
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }

    private ApplicationCompanyPermissionPolicy policy(
            ApplicationCompany company,
            CompanyRole role,
            String action,
            boolean enabled) {
        ApplicationCompanyPermissionPolicy policy = new ApplicationCompanyPermissionPolicy();
        policy.setId(new ApplicationCompanyPermissionPolicyId(company.getCompanyId(), role, action));
        policy.setCompany(company);
        policy.setEnabled(enabled);
        policy.setUpdatedBy(99L);
        return policy;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ApplicationCompany.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationCompanyPermissionPolicyJpaRepository.class)
    static class TestBootConfig {
    }
}
