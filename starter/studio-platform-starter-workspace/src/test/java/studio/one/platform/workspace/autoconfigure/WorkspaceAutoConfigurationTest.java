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
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyService;
import studio.one.platform.workspace.application.usecase.WorkspaceMemberService;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;
import studio.one.platform.workspace.application.usecase.WorkspaceTreeService;
import studio.one.platform.workspace.application.service.DefaultWorkspacePermissionService;
import studio.one.platform.workspace.application.service.WorkspaceSettings;
import studio.one.platform.workspace.web.controller.WorkspaceController;
import studio.one.platform.workspace.web.controller.WorkspaceMgmtController;

class WorkspaceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    SqlInitializationAutoConfiguration.class,
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
                    assertThat(context).doesNotHaveBean(WorkspaceController.class);
                    assertThat(context).doesNotHaveBean(WorkspaceMgmtController.class);
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
                .withPropertyValues(
                        "studio.features.workspace.enabled=true",
                        "studio.features.workspace.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WorkspaceController.class);
                    assertThat(context).hasSingleBean(WorkspaceMgmtController.class);
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
        for (String command : sql.split(";")) {
            if (!command.isBlank()) {
                statement.execute(command);
            }
        }
    }
}
