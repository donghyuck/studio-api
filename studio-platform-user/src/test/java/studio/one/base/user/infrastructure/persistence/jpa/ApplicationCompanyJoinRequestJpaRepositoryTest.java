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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.model.ApplicationCompanyMember;
import studio.one.base.user.domain.model.ApplicationCompanyMemberId;
import studio.one.base.user.domain.model.ApplicationCompanyMemberKey;
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApplicationCompanyJoinRequestJpaRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    TestEntityManager em;
    @Autowired
    ApplicationCompanyMemberKeyJpaRepository keyRepository;
    @Autowired
    ApplicationCompanyJoinRequestJpaRepository requestRepository;
    @Autowired
    ApplicationCompanyMemberJpaRepository memberRepository;
    @Test
    void storesListsAndLocksJoinRequests() {
        ApplicationCompany company = company();
        ApplicationCompanyMemberKey key = key(company);
        ApplicationCompanyJoinRequest request = request(company, key);
        em.persist(company);
        em.persist(key);
        em.persist(request);
        em.flush();
        em.clear();
        var page = requestRepository.findAllByCompanyId(company.getCompanyId(), CompanyJoinRequestStatus.PENDING, PageRequest.of(0, 10));
        var lockedRequest = requestRepository.findForUpdateById(request.getRequestId());
        var lockedKey = keyRepository.findForUpdateById(key.getKeyId());
        var lockedKeyByHash = keyRepository.findForUpdateByKeyHash(key.getKeyHash());
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getEmail()).isEqualTo("joiner@example.com");
        assertThat(requestRepository.existsPendingByKeyIdAndUserId(key.getKeyId(), request.getUserId())).isTrue();
        assertThat(requestRepository.existsPendingByCompanyIdAndUserId(company.getCompanyId(), request.getUserId())).isTrue();
        assertThat(requestRepository.countPendingByKeyId(key.getKeyId())).isEqualTo(1);
        ApplicationCompanyJoinRequest deletedUserRequest = request(
                em.find(ApplicationCompany.class, company.getCompanyId()),
                em.find(ApplicationCompanyMemberKey.class, key.getKeyId()));
        deletedUserRequest.setUserId(null);
        deletedUserRequest.setEmail("deleted-user@example.com");
        em.persist(deletedUserRequest);
        em.flush();
        assertThat(requestRepository.countPendingByKeyId(key.getKeyId())).isEqualTo(1);
        assertThat(lockedRequest).isPresent();
        assertThat(lockedRequest.get().getStatus()).isEqualTo(CompanyJoinRequestStatus.PENDING);
        assertThat(lockedKey).isPresent();
        assertThat(lockedKey.get().getKeyHash()).isEqualTo("a".repeat(64));
        assertThat(lockedKeyByHash).isPresent();
        assertThat(lockedKeyByHash.get().getKeyId()).isEqualTo(key.getKeyId());
    }
    @Test
    void pendingRequestUniquenessAndOwnerCountsUseDatabaseState() {
        ApplicationCompany company = company();
        ApplicationCompanyMemberKey key = key(company);
        ApplicationCompanyJoinRequest request = request(company, key);
        em.persist(company);
        em.persist(key);
        em.persist(request);
        em.persist(member(company, 1L, CompanyRole.OWNER));
        em.persist(member(company, 2L, CompanyRole.OWNER));
        em.persist(member(company, 3L, CompanyRole.ADMIN));
        em.flush();
        em.clear();
        em.getEntityManager().createNativeQuery("create unique index UK_TEST_COMPANY_JOIN_REQUEST_PENDING_USER\\n" + "    on TB_APPLICATION_COMPANY_JOIN_REQUEST (COMPANY_ID, USER_ID)\\n" + "    where STATUS = 'PENDING' and USER_ID is not null\\n").executeUpdate();
        assertThat(memberRepository.countByCompanyIdAndRole(company.getCompanyId(), CompanyRole.OWNER)).isEqualTo(2);
        assertThat(memberRepository.countByCompanyIdAndRole(company.getCompanyId(), CompanyRole.ADMIN)).isEqualTo(1);
        ApplicationCompany managedCompany = em.find(ApplicationCompany.class, company.getCompanyId());
        ApplicationCompanyMemberKey managedKey = em.find(ApplicationCompanyMemberKey.class, key.getKeyId());
        ApplicationCompanyJoinRequest duplicate = request(managedCompany, managedKey);
        duplicate.setEmail("duplicate@example.com");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> {
            em.persist(duplicate);
            em.flush();
        })).isInstanceOfAny(
                org.springframework.dao.DataIntegrityViolationException.class,
                org.hibernate.exception.ConstraintViolationException.class);
    }
    private ApplicationCompany company() {
        ApplicationCompany company = new ApplicationCompany();
        company.setName("join-jpa");
        company.setDisplayName("Join JPA");
        company.setStatus(CompanyStatus.ACTIVE);
        return company;
    }
    private ApplicationCompanyMemberKey key(ApplicationCompany company) {
        ApplicationCompanyMemberKey key = new ApplicationCompanyMemberKey();
        key.setCompany(company);
        key.setRole(CompanyRole.MEMBER);
        key.setKeyHash("a".repeat(64));
        key.setStatus(CompanyMemberKeyStatus.ACTIVE);
        key.setCreatedBy(99L);
        key.setUpdatedBy(99L);
        return key;
    }
    private ApplicationCompanyJoinRequest request(ApplicationCompany company, ApplicationCompanyMemberKey key) {
        ApplicationCompanyJoinRequest request = new ApplicationCompanyJoinRequest();
        request.setCompany(company);
        request.setMemberKey(key);
        request.setUserId(7L);
        request.setName("Joiner");
        request.setEmail("joiner@example.com");
        request.setRequestedRole(CompanyRole.MEMBER);
        request.setStatus(CompanyJoinRequestStatus.PENDING);
        return request;
    }
    private ApplicationCompanyMember member(ApplicationCompany company, Long userId, CompanyRole role) {
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(new ApplicationCompanyMemberId(company.getCompanyId(), userId));
        member.setCompany(company);
        member.setRole(role);
        member.setJoinedBy(99L);
        member.setUpdatedBy(99L);
        return member;
    }
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ApplicationCompany.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationCompanyJoinRequestJpaRepository.class)
    static class TestBootConfig {
    }
}
