package studio.one.base.user.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import studio.one.base.user.domain.entity.ApplicationGroup;
import studio.one.base.user.domain.entity.ApplicationGroupMemberSummary;
import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationUser;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationGroupMembershipJpaRepositorySearchTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager em;

    @Autowired
    ApplicationGroupMembershipJpaRepository repo;

    @Test
    void findMemberSummariesByGroupIdWithNullKeywordReturnsAllMembers() {
        ApplicationGroup group = group("summary-null");
        ApplicationUser alice = user("alice-null", "Alice Kim", "alice.null@example.com");
        ApplicationUser bob = user("bob-null", "Bob Lee", "bob.null@example.com");
        member(group, alice);
        member(group, bob);

        Page<ApplicationGroupMemberSummary> result = repo.findMemberSummariesByGroupId(
                group.getGroupId(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ApplicationGroupMemberSummary::getUsername)
                .containsExactlyInAnyOrder("alice-null", "bob-null");
    }

    @Test
    void findMemberSummariesByGroupIdWithBlankKeywordReturnsAllMembers() {
        ApplicationGroup group = group("summary-blank");
        ApplicationUser alice = user("alice-blank", "Alice Blank", "alice.blank@example.com");
        ApplicationUser bob = user("bob-blank", "Bob Blank", "bob.blank@example.com");
        member(group, alice);
        member(group, bob);

        Page<ApplicationGroupMemberSummary> result = repo.findMemberSummariesByGroupId(
                group.getGroupId(), "   ", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(ApplicationGroupMemberSummary::getUsername)
                .containsExactlyInAnyOrder("alice-blank", "bob-blank");
    }

    @Test
    void findMemberSummariesByGroupIdMatchesUsernameNameAndEmail() {
        ApplicationGroup group = group("summary-match");
        ApplicationUser usernameMatch = user("engineer-kim", "No Match", "username@example.com");
        ApplicationUser nameMatch = user("name-user", "Platform Owner", "name@example.com");
        ApplicationUser emailMatch = user("email-user", "Email User", "owner@example.com");
        ApplicationUser other = user("other-user", "Other User", "other@example.com");
        member(group, usernameMatch);
        member(group, nameMatch);
        member(group, emailMatch);
        member(group, other);

        List<String> usernameResults = usernames(
                repo.findMemberSummariesByGroupId(group.getGroupId(), "ENGINEER", PageRequest.of(0, 10)));
        List<String> nameResults = usernames(
                repo.findMemberSummariesByGroupId(group.getGroupId(), "platform", PageRequest.of(0, 10)));
        List<String> emailResults = usernames(
                repo.findMemberSummariesByGroupId(group.getGroupId(), "OWNER@", PageRequest.of(0, 10)));

        assertThat(usernameResults)
                .containsExactly("engineer-kim");
        assertThat(nameResults)
                .containsExactly("name-user");
        assertThat(emailResults)
                .containsExactly("email-user");
    }

    private java.util.List<String> usernames(Page<ApplicationGroupMemberSummary> page) {
        return page.getContent().stream()
                .map(ApplicationGroupMemberSummary::getUsername)
                .toList();
    }

    private ApplicationGroup group(String name) {
        return em.persistAndFlush(ApplicationGroup.builder()
                .name(name)
                .description(name)
                .build());
    }

    private ApplicationUser user(String username, String name, String email) {
        ApplicationUser user = new ApplicationUser();
        user.setUsername(username);
        user.setName(name);
        user.setEmail(email);
        user.setPassword("{noop}password");
        user.setNameVisible(true);
        user.setEmailVisible(true);
        user.setEnabled(true);
        user.setExternal(false);
        user.setFailedAttempts(0);
        return em.persistAndFlush(user);
    }

    private void member(ApplicationGroup group, ApplicationUser user) {
        em.persistAndFlush(ApplicationGroupMembership.of(group, user.getUserId(), "test"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            ApplicationGroup.class,
            ApplicationGroupMembership.class,
            ApplicationUser.class
    })
    @EnableJpaRepositories(basePackageClasses = ApplicationGroupMembershipJpaRepository.class)
    static class TestBootConfig {
    }
}
