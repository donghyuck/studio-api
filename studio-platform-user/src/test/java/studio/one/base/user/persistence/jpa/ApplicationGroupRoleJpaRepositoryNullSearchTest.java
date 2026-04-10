package studio.one.base.user.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.SpringBootConfiguration;
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
import studio.one.base.user.domain.entity.ApplicationGroupRole;
import studio.one.base.user.domain.entity.ApplicationGroupRoleId;
import studio.one.base.user.domain.entity.ApplicationGroupMembership;
import studio.one.base.user.domain.entity.ApplicationGroupWithMemberCount;
import studio.one.base.user.domain.entity.ApplicationRole;
import studio.one.base.user.domain.entity.ApplicationUser;

/**
 * Regression test: q=null 파라미터를 JPA 쿼리에 바인딩할 때 PostgreSQL이
 * 타입을 bytea로 추론하여 lower(bytea) 오류가 발생하는 것을 방지.
 *
 * CAST(:q AS String) 수정이 제거되면 이 테스트가 실패합니다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ApplicationGroupRoleJpaRepositoryNullSearchTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestEntityManager em;

    @Autowired
    ApplicationGroupRoleJpaRepository repo;

    @Test
    void findGroupsWithMemberCountByRoleId_withNullQuery_returnsAllAssignedGroups() {
        ApplicationRole role = new ApplicationRole();
        role.setName("ROLE_GROUP_NULL_SEARCH");
        role.setDescription("group null search test");
        role = em.persistAndFlush(role);

        ApplicationGroup group = ApplicationGroup.builder()
                .name("null-search-group")
                .build();
        group = em.persistAndFlush(group);

        em.persistAndFlush(ApplicationGroupRole.builder()
                .id(new ApplicationGroupRoleId(group.getGroupId(), role.getRoleId()))
                .group(group)
                .role(role)
                .build());

        Page<ApplicationGroupWithMemberCount> result = repo.findGroupsWithMemberCountByRoleId(
                role.getRoleId(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEntity().getGroupId())
                .isEqualTo(group.getGroupId());
    }

    @Test
    void findGroupsByRoleId_withNullQuery_returnsAllAssignedGroups() {
        ApplicationRole role = new ApplicationRole();
        role.setName("ROLE_GROUP_NULL_LIST");
        role.setDescription("group null list test");
        role = em.persistAndFlush(role);

        ApplicationGroup group = ApplicationGroup.builder()
                .name("null-list-group")
                .build();
        group = em.persistAndFlush(group);

        em.persistAndFlush(ApplicationGroupRole.builder()
                .id(new ApplicationGroupRoleId(group.getGroupId(), role.getRoleId()))
                .group(group)
                .role(role)
                .build());

        Page<ApplicationGroup> result = repo.findGroupsByRoleId(
                role.getRoleId(), null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getGroupId()).isEqualTo(group.getGroupId());
    }

    @Test
    void findGroupsWithMemberCountByRoleId_withNonNullQuery_filtersGroupsByKeyword() {
        ApplicationRole role = new ApplicationRole();
        role.setName("ROLE_GROUP_FILTER_SEARCH");
        role.setDescription("group filter search test");
        role = em.persistAndFlush(role);

        ApplicationGroup matchedGroup = ApplicationGroup.builder()
                .name("engineering-team")
                .description("engineering group")
                .build();
        matchedGroup = em.persistAndFlush(matchedGroup);

        ApplicationGroup unmatchedGroup = ApplicationGroup.builder()
                .name("marketing-team")
                .description("marketing group")
                .build();
        unmatchedGroup = em.persistAndFlush(unmatchedGroup);

        em.persistAndFlush(ApplicationGroupRole.builder()
                .id(new ApplicationGroupRoleId(matchedGroup.getGroupId(), role.getRoleId()))
                .group(matchedGroup)
                .role(role)
                .build());
        em.persistAndFlush(ApplicationGroupRole.builder()
                .id(new ApplicationGroupRoleId(unmatchedGroup.getGroupId(), role.getRoleId()))
                .group(unmatchedGroup)
                .role(role)
                .build());

        Page<ApplicationGroupWithMemberCount> result = repo.findGroupsWithMemberCountByRoleId(
                role.getRoleId(), "engineering", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEntity().getName()).isEqualTo("engineering-team");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {
            ApplicationGroup.class,
            ApplicationGroupMembership.class,
            ApplicationGroupRole.class,
            ApplicationRole.class,
            ApplicationUser.class
    })
    @EnableJpaRepositories(basePackageClasses = ApplicationGroupRoleJpaRepository.class)
    static class TestBootConfig {
    }
}
