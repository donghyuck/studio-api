package studio.one.platform.mybatis.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.platform.mybatis.autoconfigure.fixture.TestMapper;
import studio.one.platform.data.mybatis.StudioMyBatisProperties;

class StudioMyBatisAutoConfigurationTest {

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
    void registersMyBatisInfrastructureWithDefaultMapperLocation() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlSessionFactory.class);
            assertThat(context).hasSingleBean(SqlSessionTemplate.class);
            assertThat(context).hasSingleBean(TestMapper.class);
            assertThat(context.getBean(TestMapper.class).selectOne()).isEqualTo(1);
        });
    }

    @Test
    void usesStudioMapperLocationConvention() {
        contextRunner
                .withPropertyValues("studio.mybatis.mapper-locations=classpath*:custom-mybatis/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TestMapper.class).selectOne()).isEqualTo(2);
                });
    }

    @Test
    void keepsStandardMyBatisMapperLocationsHigherPriority() {
        contextRunner
                .withPropertyValues("mybatis.mapper-locations=classpath*:standard-mybatis/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TestMapper.class).selectOne()).isEqualTo(3);
                });
    }

    @Test
    void appliesStudioConventions() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DatabaseIdProvider.class);
            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            assertThat(factory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
        });
    }

    @Test
    void allowsCamelCaseConventionToBeDisabled() {
        contextRunner
                .withPropertyValues("studio.mybatis.map-underscore-to-camel-case=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    assertThat(factory.getConfiguration().isMapUnderscoreToCamelCase()).isFalse();
                });
    }

    @Test
    void keepsStandardMyBatisCamelCaseConfigurationHigherPriority() {
        contextRunner
                .withPropertyValues("mybatis.configuration.map-underscore-to-camel-case=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    assertThat(factory.getConfiguration().isMapUnderscoreToCamelCase()).isFalse();
                });
    }

    @Test
    void backsOffWhenDatabaseIdProviderExists() {
        DatabaseIdProvider customProvider = dataSource -> "custom";
        contextRunner
                .withBean(DatabaseIdProvider.class, () -> customProvider)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DatabaseIdProvider.class);
                    assertThat(context.getBean(DatabaseIdProvider.class)).isSameAs(customProvider);
                });
    }

    @Test
    void mergesDatabaseIdAliasesWithDefaults() {
        contextRunner
                .withPropertyValues(
                        "studio.mybatis.database-id-aliases.PostgreSQL=pg",
                        "studio.mybatis.database-id-aliases.CustomDB=custom")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    StudioMyBatisProperties properties = context.getBean(StudioMyBatisProperties.class);
                    assertThat(properties.getDatabaseIdAliases())
                            .containsEntry("PostgreSQL", "pg")
                            .containsEntry("CustomDB", "custom")
                            .containsEntry("MySQL", "mysql")
                            .containsEntry("Oracle", "oracle");
                });
    }

    @AutoConfigurationPackage(basePackageClasses = TestMapper.class)
    static class TestApplication {
    }
}
