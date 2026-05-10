package studio.one.platform.workspace.infrastructure.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.mybatis.autoconfigure.StudioMyBatisAutoConfiguration;

class WorkspaceMyBatisConventionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    StudioMyBatisAutoConfiguration.class,
                    MybatisAutoConfiguration.class))
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    @Test
    void loadsWorkspaceMapperByStudioConvention() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WorkspaceConventionTestMapper.class);
            assertThat(context.getBean(WorkspaceConventionTestMapper.class).selectConventionValue())
                    .isEqualTo(1300);
        });
    }

    @AutoConfigurationPackage(basePackageClasses = WorkspaceConventionTestMapper.class)
    static class TestApplication {
    }
}
