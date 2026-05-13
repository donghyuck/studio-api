package studio.one.base.user.infrastructure.persistence.jpa;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyMember;
import studio.one.base.user.domain.model.ApplicationCompanyMemberId;
import studio.one.base.user.domain.model.ApplicationUser;
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationUserJpaRepositoryCompanyFilterTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    TestEntityManager em;
    @Autowired
    ApplicationUserJpaRepository repository;
    @Test
    void findUsersByCompanyIdFiltersCompanyMembersAndAppliesSort() {
        TestData data = persistTestData();
        em.flush();
        em.clear();
        var page = repository.findUsersByCompanyId(
                data.companyId(),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "username")));
        assertThat(page.getContent())
                .extracting(ApplicationUser::getUsername)
                .containsExactly("kim.viewer", "kim.owner");
    }
    @Test
    void searchByCompanyIdFiltersKeywordAndAppliesSort() {
        TestData data = persistTestData();
        em.flush();
        em.clear();
        var page = repository.searchByCompanyId(
                data.companyId(),
                "owner",
                PageRequest.of(0, 10, Sort.by("email")));
        assertThat(page.getContent())
                .extracting(ApplicationUser::getUsername)
                .containsExactly("kim.owner");
    }
    private TestData persistTestData() {
        ApplicationCompany company = company("company-a");
        em.persist(company);
        ApplicationCompany otherCompany = company("company-b");
        em.persist(otherCompany);
        ApplicationUser owner = user("kim.owner", "Kim Owner", "owner@test.com");
        em.persist(owner);
        ApplicationUser viewer = user("kim.viewer", "Kim Viewer", "viewer@test.com");
        em.persist(viewer);
        ApplicationUser other = user("lee.other", "Lee Other", "other@test.com");
        em.persist(other);
        em.flush();
        em.persist(member(company, owner, CompanyRole.OWNER));
        em.persist(member(company, viewer, CompanyRole.MEMBER));
        em.persist(member(otherCompany, other, CompanyRole.OWNER));
        return new TestData(company.getCompanyId());
    }
    private ApplicationCompany company(String name) {
        ApplicationCompany company = new ApplicationCompany();
        company.setName(name);
        company.setDisplayName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }
    private ApplicationUser user(String username, String name, String email) {
        return ApplicationUser.builder()
                .username(username)
                .name(name)
                .email(email)
                .enabled(true)
                .nameVisible(true)
                .emailVisible(true)
                .external(false)
                .build();
    }
    private ApplicationCompanyMember member(ApplicationCompany company, ApplicationUser user, CompanyRole role) {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(company.getCompanyId(), user.getUserId()));
        member.setCompany(company);
        member.setRole(role);
        member.setJoinedAt(Instant.now());
        return member;
    }
    private static final class TestData {
        private final Long companyId;
        private TestData(Long companyId) {
            this.companyId = companyId;
        }
        private Long companyId() { return companyId; }
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            ApplicationCompany.class,
            ApplicationCompanyMember.class,
            ApplicationUser.class
    })
    @EnableJpaRepositories(basePackageClasses = ApplicationUserJpaRepository.class)
    static class TestBootConfig {
    }
}
