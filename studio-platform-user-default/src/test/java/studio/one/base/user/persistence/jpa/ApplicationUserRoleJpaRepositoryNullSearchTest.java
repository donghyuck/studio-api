package studio.one.base.user.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;
import studio.one.base.user.domain.entity.ApplicationUserRole;

/**
 * Regression test: q=null 파라미터를 JPA 쿼리에 바인딩할 때 PostgreSQL이
 * 타입을 bytea로 추론하여 lower(bytea) 오류가 발생하는 것을 방지.
 *
 * CAST(:q AS String) 수정이 제거되면 이 테스트가 실패합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationUserRoleJpaRepositoryNullSearchTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager em;

    @Autowired
    ApplicationUserRoleJpaRepository repo;

    @Test
    void findUserIdsByRoleId_withNullQuery_returnsAllAssignedUsers() {
        ApplicationRole role = new ApplicationRole();
        role.setName("ROLE_NULL_SEARCH");
        role.setDescription("null search test role");
        role = em.persistAndFlush(role);

        ApplicationUser user = ApplicationUser.builder()
                .username("null.search.user")
                .email("null.search@test.com")
                .enabled(true)
                .build();
        user = em.persistAndFlush(user);

        em.persistAndFlush(ApplicationUserRole.of(user.getUserId(), role, "test"));

        Page<Long> result = repo.findUserIdsByRoleId(role.getRoleId(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(user.getUserId());
    }

    @Test
    void findUserIdsByRoleId_withNonNullQuery_filtersUsersByKeyword() {
        ApplicationRole role = new ApplicationRole();
        role.setName("ROLE_FILTER_SEARCH");
        role.setDescription("filter search test role");
        role = em.persistAndFlush(role);

        ApplicationUser alice = ApplicationUser.builder()
                .username("alice.matched")
                .email("alice@test.com")
                .enabled(true)
                .build();
        alice = em.persistAndFlush(alice);

        ApplicationUser bob = ApplicationUser.builder()
                .username("bob.unmatched")
                .email("bob@test.com")
                .enabled(true)
                .build();
        bob = em.persistAndFlush(bob);

        em.persistAndFlush(ApplicationUserRole.of(alice.getUserId(), role, "test"));
        em.persistAndFlush(ApplicationUserRole.of(bob.getUserId(), role, "test"));

        Page<Long> result = repo.findUserIdsByRoleId(role.getRoleId(), "alice", PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(alice.getUserId());
        assertThat(result.getContent()).doesNotContain(bob.getUserId());
    }
}
