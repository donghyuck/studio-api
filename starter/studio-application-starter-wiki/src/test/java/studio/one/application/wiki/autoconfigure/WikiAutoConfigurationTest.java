package studio.one.application.wiki.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.application.wiki.domain.model.WikiPermissionActions;
import studio.one.application.wiki.application.usecase.WikiPageService;
import studio.one.application.wiki.web.controller.WikiController;
import studio.one.application.wiki.web.controller.WikiMgmtController;
import studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration;
import studio.one.platform.workspace.application.usecase.WorkspacePermissionService;

class WikiAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    WikiPermissionAutoConfiguration.class,
                    WorkspaceAutoConfiguration.class,
                    WikiAutoConfiguration.class,
                    WikiWebAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "studio.features.workspace.enabled=true");

    @Test
    void doesNotRegisterWikiBeansWhenFeatureDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(WikiPageService.class);
            assertThat(context).doesNotHaveBean(WikiController.class);
            assertThat(context).doesNotHaveBean(WikiMgmtController.class);
        });
    }

    @Test
    void registersJpaServicesWhenFeatureEnabled() {
        contextRunner
                .withPropertyValues("studio.features.wiki.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WikiPageService.class);
                    assertThat(context).doesNotHaveBean(WikiController.class);
                    assertThat(context).doesNotHaveBean(WikiMgmtController.class);
                });
    }

    @Test
    void contributesWikiPermissionDefinitionsBeforeWorkspacePermissionServiceCreation() {
        contextRunner
                .withPropertyValues("studio.features.wiki.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    WorkspacePermissionService permissionService = context.getBean(WorkspacePermissionService.class);
                    assertThat(permissionService.getPermissionDefinitions())
                            .anySatisfy(definition -> assertThat(definition.action())
                                    .isEqualTo(WikiPermissionActions.PAGE_READ));
                });
    }

    @Test
    void registersWebControllersOnlyWhenWebFeatureEnabled() {
        contextRunner
                .withPropertyValues(
                        "studio.features.wiki.enabled=true",
                        "studio.features.wiki.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(WikiController.class);
                    assertThat(context).hasSingleBean(WikiMgmtController.class);
                });
    }

    @Test
    void doesNotRegisterDefaultServiceForUnsupportedPersistence() {
        contextRunner
                .withPropertyValues(
                        "studio.features.wiki.enabled=true",
                        "studio.features.wiki.persistence=jdbc",
                        "studio.features.wiki.web.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WikiPageService.class);
                    assertThat(context).doesNotHaveBean(WikiController.class);
                    assertThat(context).doesNotHaveBean(WikiMgmtController.class);
                });
    }
}
