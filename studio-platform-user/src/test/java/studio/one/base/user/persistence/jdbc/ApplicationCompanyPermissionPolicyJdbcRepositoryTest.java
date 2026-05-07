package studio.one.base.user.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.company.permission.CompanyPermissionActions;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicy;
import studio.one.base.user.domain.entity.ApplicationCompanyPermissionPolicyId;

@Testcontainers
class ApplicationCompanyPermissionPolicyJdbcRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private NamedParameterJdbcTemplate template;
    private DriverManagerDataSource dataSource;
    private ApplicationCompanyPermissionPolicyJdbcRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
        template = new NamedParameterJdbcTemplate(dataSource);
        repository = new ApplicationCompanyPermissionPolicyJdbcRepository(template);
        recreateSchema();
    }

    @Test
    void storesListsAndDeletesPoliciesByCompany() {
        repository.save(policy(CompanyRole.ADMIN, CompanyPermissionActions.READ, true));
        repository.save(policy(CompanyRole.ADMIN, CompanyPermissionActions.MEMBER_MANAGE, false));

        var policies = repository.findAllByCompanyId(10L);

        assertThat(policies).hasSize(2);
        assertThat(policies).filteredOn(policy -> policy.getId().getAction().equals(CompanyPermissionActions.MEMBER_MANAGE))
                .singleElement()
                .satisfies(policy -> assertThat(policy.isEnabled()).isFalse());

        repository.deleteAllByCompanyId(10L);

        assertThat(repository.findAllByCompanyId(10L)).isEmpty();
    }

    private ApplicationCompanyPermissionPolicy policy(CompanyRole role, String action, boolean enabled) {
        ApplicationCompanyPermissionPolicy policy = new ApplicationCompanyPermissionPolicy();
        policy.setId(new ApplicationCompanyPermissionPolicyId(10L, role, action));
        policy.setEnabled(enabled);
        policy.setUpdatedBy(99L);
        return policy;
    }

    private void recreateSchema() throws SQLException {
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY_PERMISSION_POLICY");
        template.getJdbcTemplate().execute("drop table if exists TB_APPLICATION_COMPANY");
        template.getJdbcTemplate().execute("create table TB_APPLICATION_COMPANY (COMPANY_ID bigint primary key)");
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("schema/user/postgres/V304__create_company_permission_policy.sql"));
        }
        template.getJdbcTemplate().execute("insert into TB_APPLICATION_COMPANY (COMPANY_ID) values (10)");
    }
}
