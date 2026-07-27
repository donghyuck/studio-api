package studio.one.base.user.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ApplicationUserJdbcRepositoryCompanyFilterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate template;
    private DriverManagerDataSource dataSource;
    private ApplicationUserJdbcRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        template = new NamedParameterJdbcTemplate(dataSource);
        repository = new ApplicationUserJdbcRepository(template);
        recreateSchema();
    }

    @Test
    void findUsersByCompanyIdFiltersCompanyMembersAndAppliesAllowlistedSort() {
        var page = repository.findUsersByCompanyId(
                10L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "username")));

        assertThat(page.getContent())
                .extracting(user -> user.getUsername())
                .containsExactly("kim.viewer", "kim.owner");
    }

    @Test
    void searchByCompanyIdFiltersKeywordAndSortsByMappedColumn() {
        var page = repository.searchByCompanyId(
                10L,
                "owner",
                PageRequest.of(0, 10, Sort.by("email")));

        assertThat(page.getContent())
                .extracting(user -> user.getUsername())
                .containsExactly("kim.owner");
    }

    @Test
    void findUsersByCompanyIdAllowsApplicationUserStateSorts() {
        var page = repository.findUsersByCompanyId(
                10L,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "failedAttempts")));

        assertThat(page.getContent())
                .extracting(user -> user.getUsername())
                .containsExactly("kim.viewer", "kim.owner");
    }

    private void recreateSchema() throws SQLException {
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_USER_PROPERTY");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY_MEMBERS");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_USER");
        template.getJdbcTemplate().execute("""
                create table TB_APPLICATION_USER (
                    USER_ID bigint primary key,
                    USERNAME varchar(255) not null,
                    NAME varchar(255),
                    FIRST_NAME varchar(255),
                    LAST_NAME varchar(255),
                    PASSWORD_HASH varchar(255),
                    NAME_VISIBLE boolean not null default true,
                    EMAIL varchar(255) not null,
                    EMAIL_VISIBLE boolean not null default true,
                    USER_ENABLED boolean not null default true,
                    USER_EXTERNAL boolean not null default false,
                    STATUS integer,
                    FAILED_ATTEMPTS integer not null default 0,
                    LAST_FAILED_AT timestamp,
                    ACCOUNT_LOCKED_UNTIL timestamp,
                    CREATION_DATE timestamp,
                    MODIFIED_DATE timestamp
                )
                """);
        template.getJdbcTemplate().execute("""
                create table TB_APPLICATION_USER_PROPERTY (
                    USER_ID bigint not null,
                    PROPERTY_NAME varchar(100) not null,
                    PROPERTY_VALUE varchar(1024) not null
                )
                """);
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY (COMPANY_ID bigint primary key)");
        template.getJdbcTemplate().execute("""
                create table TB_APPLICATION_COMPANY_MEMBERS (
                    COMPANY_ID bigint not null,
                    USER_ID bigint not null,
                    ROLE varchar(30) not null,
                    STATUS varchar(30) not null,
                    JOINED_AT timestamp not null,
                    primary key (COMPANY_ID, USER_ID)
                )
                """);
        template.getJdbcTemplate().update("""
                insert into TB_APPLICATION_USER
                    (USER_ID, USERNAME, NAME, EMAIL, STATUS, FAILED_ATTEMPTS, CREATION_DATE, MODIFIED_DATE)
                values
                    (1, 'kim.owner', 'Kim Owner', 'owner@test.com', 0, 1, now(), now()),
                    (2, 'kim.viewer', 'Kim Viewer', 'viewer@test.com', 0, 5, now(), now()),
                    (3, 'lee.other', 'Lee Other', 'other@test.com', 0, 9, now(), now())
                """);
        template.getJdbcTemplate().update("insert into TB_APPLICATION_COMPANY (COMPANY_ID) values (10), (20)");
        template.getJdbcTemplate().update("""
                insert into TB_APPLICATION_COMPANY_MEMBERS
                    (COMPANY_ID, USER_ID, ROLE, STATUS, JOINED_AT)
                values
                    (10, 1, 'OWNER', 'ACTIVE', now()),
                    (10, 2, 'VIEWER', 'ACTIVE', now()),
                    (20, 3, 'OWNER', 'ACTIVE', now())
                """);
    }
}
