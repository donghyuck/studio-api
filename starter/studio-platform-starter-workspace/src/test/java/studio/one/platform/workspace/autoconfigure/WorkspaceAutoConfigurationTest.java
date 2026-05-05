package studio.one.platform.workspace.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.workspace.service.WorkspaceMemberService;
import studio.one.platform.workspace.service.WorkspacePermissionService;
import studio.one.platform.workspace.service.WorkspaceTreeService;
import studio.one.platform.workspace.web.controller.WorkspaceController;
import studio.one.platform.workspace.web.controller.WorkspaceMgmtController;

class WorkspaceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
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
}
