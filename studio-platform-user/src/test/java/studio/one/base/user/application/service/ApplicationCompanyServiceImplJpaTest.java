package studio.one.base.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.infrastructure.persistence.jpa.ApplicationCompanyJpaRepository;
import studio.one.base.user.application.usecase.ApplicationCompanyService;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationCompanyServiceImplJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EntityManager entityManager;

    @Autowired
    ApplicationCompanyService companyService;

    @Test
    void searchReturnsCompaniesWithPropertiesReadableAfterServiceTransaction() {
        Long companyId = persistCompany("acme-list", "Acme List", Map.of("tier", "enterprise"));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var result = companyService.search("", PageRequest.of(0, 15, Sort.by(Sort.Order.desc("companyId"))));

        assertThat(result.getContent())
                .extracting(ApplicationCompany::getCompanyId)
                .contains(companyId);
        ApplicationCompany company = result.getContent().stream()
                .filter(item -> companyId.equals(item.getCompanyId()))
                .findFirst()
                .orElseThrow();
        assertThat(company.getProperties()).containsEntry("tier", "enterprise");
    }

    @Test
    void getReturnsCompanyWithPropertiesReadableAfterServiceTransaction() {
        Long companyId = persistCompany("acme-get", "Acme Get", Map.of("tier", "standard"));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        ApplicationCompany company = companyService.get(companyId);

        assertThat(company.getProperties()).containsEntry("tier", "standard");
    }

    private Long persistCompany(String name, String displayName, Map<String, String> properties) {
        ApplicationCompany company = new ApplicationCompany();
        company.setName(name);
        company.setDisplayName(displayName);
        company.setProperties(properties);
        entityManager.persist(company);
        entityManager.flush();
        return company.getCompanyId();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ApplicationCompany.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationCompanyJpaRepository.class)
    @Import(ApplicationCompanyServiceImpl.class)
    static class TestBootConfig {
    }
}
