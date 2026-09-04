package studio.one.platform.team.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.team.application.usecase.TeamAuthorizationPort;
import studio.one.platform.team.application.usecase.TeamMemberService;
import studio.one.platform.team.application.usecase.TeamJoinService;
import studio.one.platform.team.application.usecase.TeamMigrationKnowledgePort;
import studio.one.platform.team.application.usecase.TeamMigrationService;
import studio.one.platform.team.application.usecase.TeamMigrationWorkspacePort;
import studio.one.platform.team.application.usecase.TeamService;
import studio.one.platform.team.web.controller.TeamController;
import studio.one.platform.team.web.controller.TeamMigrationMgmtController;

class TeamAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    TeamAutoConfiguration.class,
                    TeamWebAutoConfiguration.class,
                    TeamMigrationAutoConfiguration.class,
                    TeamMigrationWebAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    void featureIsOptIn() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TeamService.class);
            assertThat(context).doesNotHaveBean(TeamController.class);
            assertThat(context).doesNotHaveBean(TeamMigrationService.class);
        });
    }

    @Test
    void registersJpaServicesWithoutRequiringCompanyAssignment() {
        contextRunner
                .withPropertyValues("studio.features.team.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamService.class);
                    assertThat(context).hasSingleBean(TeamMemberService.class);
                    assertThat(context).hasSingleBean(TeamJoinService.class);
                    assertThat(context).hasSingleBean(TeamAuthorizationPort.class);
                    assertThat(context).doesNotHaveBean(TeamController.class);
                    assertThat(context).doesNotHaveBean(TeamMigrationService.class);
                });
    }

    @Test
    void registersWebControllerOnlyWhenWebFeatureEnabled() {
        contextRunner
                .withPropertyValues(
                        "studio.features.team.enabled=true",
                        "studio.features.team.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamController.class);
                    assertThat(context).doesNotHaveBean(TeamMigrationMgmtController.class);
                });
    }

    @Test
    void registersMigrationEngineOnlyWhenBothPrimitiveAdaptersExist() {
        contextRunner
                .withBean(TeamMigrationWorkspacePort.class,
                        () -> org.mockito.Mockito.mock(TeamMigrationWorkspacePort.class))
                .withBean(TeamMigrationKnowledgePort.class,
                        () -> org.mockito.Mockito.mock(TeamMigrationKnowledgePort.class))
                .withPropertyValues(
                        "studio.features.team.enabled=true",
                        "studio.features.team.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamMigrationService.class);
                    assertThat(context).hasSingleBean(TeamMigrationMgmtController.class);
                });
    }
}
