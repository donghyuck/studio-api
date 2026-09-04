package studio.one.platform.workspace.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.application.service.DefaultWorkspacePermissionService;
import studio.one.platform.workspace.application.service.WorkspaceSettings;
import studio.one.platform.workspace.web.controller.WorkspaceController;
import studio.one.platform.workspace.web.controller.WorkspaceMgmtController;
import studio.one.platform.workspace.web.controller.TeamWorkspaceController;

class WorkspaceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    DataSourceInitializationAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    WorkspaceAutoConfiguration.class,
                    WorkspaceWebAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    void doesNotRegisterWorkspaceBeansWhenFeatureDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WorkspaceTreeService.class);
            assertThat(context).doesNotHaveBean(WorkspacePermissionService.class);
            assertThat(context).doesNotHaveBean(WorkspaceController.class);
            assertThat(context).doesNotHaveBean(WorkspaceMgmtController.class);
        });
    }

    @Test
    void registersJpaServicesWhenFeatureEnabled() {
        contextRunner
                .withPropertyValues("studio.features.workspace.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceTreeService.class);
                    assertThat(context).hasSingleBean(WorkspaceMemberService.class);
                    assertThat(context).hasSingleBean(WorkspacePermissionService.class);
                    assertThat(context).hasSingleBean(TeamMigrationWorkspacePort.class);
                    assertThat(context).doesNotHaveBean(WorkspaceController.class);
                    assertThat(context).doesNotHaveBean(WorkspaceMgmtController.class);
                    assertThat(context).doesNotHaveBean(TeamWorkspaceController.class);
                });
    }

    @Test
    void mapsCompanyRequiredPropertyToWorkspaceSettings() {
        contextRunner
                .withBean(ApplicationCompanyService.class,
                        () -> org.mockito.Mockito.mock(ApplicationCompanyService.class))
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.company-required=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceSettings.class);
                    assertThat(context.getBean(WorkspaceSettings.class).companyRequired()).isTrue();
                });
    }

    @Test
    void companyScopeEnforcedFailsFastWhenV1302IndexesAreMissing() {
        contextRunner
                .withBean(ApplicationCompanyService.class,
                        () -> org.mockito.Mockito.mock(ApplicationCompanyService.class))
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.company-required=true",
                        "studio.features.workspace.company-scope-enforced=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("company-scope-enforced=true requires V1302");
                });
    }

    @Test
    void companyScopeEnforcedStartsWhenV1302SchemaShapeExists() {
        contextRunner
                .withBean(ApplicationCompanyService.class,
                        () -> org.mockito.Mockito.mock(ApplicationCompanyService.class))
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.company-required=true",
                        "studio.features.workspace.company-scope-enforced=true",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.sql.init.mode=always",
                        "spring.sql.init.schema-locations=classpath:workspace-company-scope-v1302-h2.sql")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceAutoConfiguration.WorkspaceCompanyScopeEnforcementGuard.class);
                });
    }

    @Test
    void v1302SchemaShapeRequiresCompanyScopeEnforcedProperty() {
        contextRunner
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.sql.init.mode=always",
                        "spring.sql.init.schema-locations=classpath:workspace-company-scope-v1302-h2.sql")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("V1302 workspace company scope schema requires");
                });
    }

    @Test
    void companyScopeEnforcedRequiresCompanyRequired() {
        contextRunner
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.company-scope-enforced=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("company-scope-enforced=true requires studio.features.workspace.company-required=true");
                });
    }

    @Test
    void companyRequiredRequiresCompanyService() {
        contextRunner
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.company-required=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("requires ApplicationCompanyService");
                });
    }

    @Test
    void v1303CompanyForeignKeyRejectsOrphanWorkspaceRows() throws Exception {
        assertV1303RejectsOrphanWorkspaceRows("PostgreSQL", "postgres");
        assertV1303RejectsOrphanWorkspaceRows("MySQL", "mysql");
        assertV1303RejectsOrphanWorkspaceRows("MySQL", "mariadb");
    }

    @Test
    void v1303CompanyForeignKeyRejectsExistingOrphanWorkspaceRows() throws Exception {
        assertV1303RejectsExistingOrphanWorkspaceRows("PostgreSQL", "postgres");
        assertV1303RejectsExistingOrphanWorkspaceRows("MySQL", "mysql");
        assertV1303RejectsExistingOrphanWorkspaceRows("MySQL", "mariadb");
    }

    @Test
    void v1801TeamScopeEnforcesOwnershipAndV1804AllowsMultipleRootsAcrossDialects() throws Exception {
        String postgres = new String(new ClassPathResource(
                "schema/workspace/postgres/V1801__add_workspace_team_scope.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(postgres)
                .contains("CHECK (TEAM_ID IS NOT NULL OR COMPANY_ID IS NOT NULL)")
                .contains("FOREIGN KEY (TEAM_ID) REFERENCES TB_PLATFORM_TEAM(TEAM_ID)")
                .contains("UK_PLATFORM_WORKSPACE_TEAM_ROOT");
        String postgresV1804 = new String(new ClassPathResource(
                "schema/workspace/postgres/V1804__allow_multiple_team_workspace_roots.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(postgresV1804).contains("DROP INDEX IF EXISTS UK_PLATFORM_WORKSPACE_TEAM_ROOT");
        assertV1801TeamScope("MySQL", "mysql", true);
        assertV1801TeamScope("MySQL", "mariadb", true);
    }

    private static void assertV1801TeamScope(String h2Mode, String dialect, boolean mysqlFamily) throws Exception {
        String databaseName = "workspace_team_scope_" + dialect + "_" + System.nanoTime();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()) {
            statement.execute("create table TB_PLATFORM_TEAM (TEAM_ID BIGINT primary key)");
            statement.execute("insert into TB_PLATFORM_TEAM (TEAM_ID) values (10)");
            if (mysqlFamily) {
                statement.execute("""
                        create table TB_PLATFORM_WORKSPACE (
                            WORKSPACE_ID BIGINT primary key,
                            PARENT_ID BIGINT null,
                            COMPANY_ID BIGINT not null,
                            SLUG VARCHAR(100) not null,
                            PATH VARCHAR(1024) not null,
                            PATH_HASH BINARY(32),
                            PARENT_KEY BIGINT
                        )
                        """);
                statement.execute("create unique index UK_PLATFORM_WORKSPACE_COMPANY_PATH "
                        + "on TB_PLATFORM_WORKSPACE (COMPANY_ID, PATH_HASH)");
                statement.execute("create unique index UK_PLATFORM_WORKSPACE_COMPANY_PARENT_SLUG "
                        + "on TB_PLATFORM_WORKSPACE (COMPANY_ID, PARENT_KEY, SLUG)");
            } else {
                statement.execute("""
                        create table TB_PLATFORM_WORKSPACE (
                            WORKSPACE_ID BIGINT primary key,
                            PARENT_ID BIGINT null,
                            COMPANY_ID BIGINT not null,
                            SLUG VARCHAR(100) not null,
                            PATH VARCHAR(1024) not null
                        )
                        """);
            }
            String migration = new String(new ClassPathResource(
                    "schema/workspace/" + dialect + "/V1801__add_workspace_team_scope.sql")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            // H2's MySQL mode supports generated columns but not the MySQL/MariaDB STORED keyword.
            executeSql(statement, migration.replace(") STORED", ")"));

            statement.execute("""
                    insert into TB_PLATFORM_WORKSPACE
                        (WORKSPACE_ID, PARENT_ID, COMPANY_ID, TEAM_ID, SLUG, PATH, ACCESS_MODE)
                    values (1, null, null, 10, 'root', 'root', 'INHERIT')
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_WORKSPACE
                        (WORKSPACE_ID, PARENT_ID, COMPANY_ID, TEAM_ID, SLUG, PATH, ACCESS_MODE)
                    values (2, null, null, 10, 'second', 'second', 'INHERIT')
                    """))
                    .isInstanceOf(SQLException.class);
            String forestMigration = new String(new ClassPathResource(
                    "schema/workspace/" + dialect + "/V1804__allow_multiple_team_workspace_roots.sql")
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            executeSql(statement, forestMigration);
            statement.execute("""
                    insert into TB_PLATFORM_WORKSPACE
                        (WORKSPACE_ID, PARENT_ID, COMPANY_ID, TEAM_ID, SLUG, PATH, ACCESS_MODE)
                    values (2, null, null, 10, 'second', 'second', 'INHERIT')
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_WORKSPACE
                        (WORKSPACE_ID, PARENT_ID, COMPANY_ID, TEAM_ID, SLUG, PATH, ACCESS_MODE)
                    values (3, null, null, null, 'orphan', 'orphan', 'INHERIT')
                    """))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_WORKSPACE
                        (WORKSPACE_ID, PARENT_ID, COMPANY_ID, TEAM_ID, SLUG, PATH, ACCESS_MODE)
                    values (4, null, null, 999, 'foreign', 'foreign', 'INHERIT')
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertV1303RejectsOrphanWorkspaceRows(String h2Mode, String dialect) throws Exception {
        String databaseName = "workspace_fk_" + dialect + "_" + System.nanoTime();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()) {
            statement.execute("create table TB_APPLICATION_COMPANY (COMPANY_ID BIGINT primary key)");
            statement.execute("create table TB_PLATFORM_WORKSPACE (WORKSPACE_ID BIGINT primary key, COMPANY_ID BIGINT not null)");
            statement.execute("insert into TB_APPLICATION_COMPANY (COMPANY_ID) values (10)");
            statement.execute("insert into TB_PLATFORM_WORKSPACE (WORKSPACE_ID, COMPANY_ID) values (1, 10)");
            executeSqlResource(statement, "schema/workspace/" + dialect + "/V1303__add_workspace_company_fk.sql");

            assertThatThrownBy(() -> statement.execute(
                    "insert into TB_PLATFORM_WORKSPACE (WORKSPACE_ID, COMPANY_ID) values (2, 999)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static void assertV1303RejectsExistingOrphanWorkspaceRows(String h2Mode, String dialect) throws Exception {
        String databaseName = "workspace_fk_orphan_" + dialect + "_" + System.nanoTime();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                Statement statement = connection.createStatement()) {
            statement.execute("create table TB_APPLICATION_COMPANY (COMPANY_ID BIGINT primary key)");
            statement.execute("create table TB_PLATFORM_WORKSPACE (WORKSPACE_ID BIGINT primary key, COMPANY_ID BIGINT not null)");
            statement.execute("insert into TB_PLATFORM_WORKSPACE (WORKSPACE_ID, COMPANY_ID) values (1, 999)");

            assertThatThrownBy(() -> executeSqlResource(
                    statement,
                    "schema/workspace/" + dialect + "/V1303__add_workspace_company_fk.sql"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void companyOwnerOverrideIsOptInEvenWhenCompanyMemberServiceExists() {
        contextRunner
                .withBean(ApplicationCompanyMemberService.class,
                        () -> org.mockito.Mockito.mock(ApplicationCompanyMemberService.class))
                .withPropertyValues("studio.features.workspace.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DefaultWorkspacePermissionService service = context.getBean(DefaultWorkspacePermissionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "companyMemberService")).isNull();
                });
    }

    @Test
    void companyOwnerOverrideInjectsCompanyMemberServiceWhenEnabled() {
        contextRunner
                .withBean(ApplicationCompanyMemberService.class,
                        () -> org.mockito.Mockito.mock(ApplicationCompanyMemberService.class))
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.workspace.permission.company-owner-override-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DefaultWorkspacePermissionService service = context.getBean(DefaultWorkspacePermissionService.class);
                    assertThat(ReflectionTestUtils.getField(service, "companyMemberService")).isNotNull();
                });
    }

    @Test
    void companyOwnerOverrideFailsFastWhenCompanyMemberServiceIsMissing() {
        contextRunner
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.workspace.permission.company-owner-override-enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("company-owner-override-enabled requires ApplicationCompanyMemberService");
                });
    }

    @Test
    void registersWebControllersOnlyWhenWebFeatureEnabled() {
        contextRunner
                .withBean(studio.one.platform.team.application.usecase.TeamAuthorizationPort.class,
                        () -> org.mockito.Mockito.mock(
                                studio.one.platform.team.application.usecase.TeamAuthorizationPort.class))
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceController.class);
                    assertThat(context).hasSingleBean(WorkspaceMgmtController.class);
                    assertThat(context).hasSingleBean(TeamWorkspaceController.class);
                });
    }

    @Test
    void doesNotRegisterDefaultServicesForUnsupportedPersistence() {
        contextRunner
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.persistence=jdbc",
                        "studio.features.workspace.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WorkspaceTreeService.class);
                    assertThat(context).doesNotHaveBean(WorkspaceMemberService.class);
                    assertThat(context).doesNotHaveBean(WorkspacePermissionService.class);
                    assertThat(context).doesNotHaveBean(WorkspaceController.class);
                    assertThat(context).doesNotHaveBean(WorkspaceMgmtController.class);
                });
    }

    private static void executeSqlResource(Statement statement, String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        executeSql(statement, sql);
    }

    private static void executeSql(Statement statement, String sql) throws Exception {
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }
}
