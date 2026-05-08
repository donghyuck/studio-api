package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studio.one.platform.ai.adapters.vector.PgVectorStoreAdapterV2;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.mybatis.autoconfigure.StudioMyBatisAutoConfiguration;

class VectorStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    StudioMyBatisAutoConfiguration.class,
                    MybatisAutoConfiguration.class,
                    VectorStoreConfiguration.class))
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.datasource.generate-unique-name=true",
                    "spring.datasource.driver-class-name=org.h2.Driver");

    @Test
    void registersPgVectorStoreWithDefaultMapperLocations() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PgVectorMapper.class);
            assertThat(context).hasSingleBean(VectorStorePort.class);
            assertThat(context.getBean(VectorStorePort.class)).isInstanceOf(PgVectorStoreAdapterV2.class);
        });
    }

    @Test
    void registersJdbcFallbackVectorStoreWhenMyBatisIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        DataSourceAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        VectorStoreConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.generate-unique-name=true",
                        "spring.datasource.driver-class-name=org.h2.Driver")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PgVectorMapper.class);
                    assertThat(context).hasSingleBean(VectorStorePort.class);
                    assertThat(context.getBean(VectorStorePort.class)).isInstanceOf(PgVectorStoreAdapterV2.class);
                });
    }

    @Test
    void acceptsCustomNamedSqlSessionTemplate() {
        contextRunner
                .withUserConfiguration(CustomSqlSessionTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("customSqlSessionTemplate");
                    assertThat(context).hasSingleBean(PgVectorMapper.class);
                    assertThat(context).hasSingleBean(VectorStorePort.class);
                });
    }

    @Test
    void fallsBackWhenPgVectorMapperXmlIsMissing() {
        contextRunner
                .withPropertyValues("mybatis.mapper-locations=classpath*:missing-ai-mapper/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PgVectorMapper.class);
                    assertThat(context).hasSingleBean(VectorStorePort.class);
                    assertThat(context.getBean(VectorStorePort.class)).isInstanceOf(PgVectorStoreAdapterV2.class);
                });
    }

    @Test
    void doesNotAssertPgVectorMapperWhenJdbcTemplateIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        StudioMyBatisAutoConfiguration.class,
                        MybatisAutoConfiguration.class,
                        VectorStoreConfiguration.class))
                .withBean(DataSource.class, () -> new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:h2:mem:no-jdbc-template;DB_CLOSE_DELAY=-1", "sa", ""))
                .withPropertyValues("mybatis.mapper-locations=classpath*:missing-ai-mapper/**/*.xml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PgVectorMapper.class);
                    assertThat(context).doesNotHaveBean(VectorStorePort.class);
                });
    }

    @AutoConfigurationPackage(basePackageClasses = PgVectorMapper.class)
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomSqlSessionTemplateConfig {

        @Bean("customSqlSessionTemplate")
        SqlSessionTemplate customSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }
    }
}
