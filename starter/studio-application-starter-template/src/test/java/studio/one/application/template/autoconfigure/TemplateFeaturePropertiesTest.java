package studio.one.application.template.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.application.template.persistence.TemplatePersistenceRepository;
import studio.one.application.template.persistence.jdbc.TemplateJdbcRepository;
import studio.one.application.template.persistence.jpa.repo.TemplateJpaPersistenceRepository;
import studio.one.application.template.persistence.jpa.repo.TemplateJpaRepository;
import studio.one.application.template.service.TemplatesService;
import studio.one.application.template.service.impl.FreemarkerTemplateBuilder;
import studio.one.application.template.service.impl.TemplatesServiceImpl;
import studio.one.application.template.web.controller.TemplateMgmtController;
import studio.one.platform.autoconfigure.PersistenceProperties;

class TemplateFeaturePropertiesTest {

    @Test
    void featurePropertyResolverLeavesGlobalMyBatisRaw() {
        TemplateFeatureProperties properties = new TemplateFeatureProperties();

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.mybatis));
    }

    @Test
    void featurePropertyResolverLeavesExplicitMyBatisRaw() {
        TemplateFeatureProperties properties = new TemplateFeatureProperties();
        properties.setPersistence(PersistenceProperties.Type.mybatis);

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.jpa));
    }

    @Test
    void globalMyBatisSelectsJdbcCompatibilityService() {
        TemplateAutoConfiguration configuration = new TemplateAutoConfiguration();
        TemplateFeatureProperties template = new TemplateFeatureProperties();
        PersistenceProperties persistence = new PersistenceProperties(PersistenceProperties.Type.mybatis);

        TemplatesServiceImpl service = configuration.templatesService(template, persistence,
                provider(new TemplateJpaPersistenceRepository(stub(TemplateJpaRepository.class))),
                provider(new TemplateJdbcRepository(jdbcTemplate("PostgreSQL"))),
                new FreemarkerTemplateBuilder(null, null));

        assertThat(ReflectionTestUtils.getField(service, "templateRepository"))
                .isInstanceOf(TemplateJdbcRepository.class);
    }

    @Test
    void explicitJpaOverrideSelectsJpaRepository() {
        TemplateAutoConfiguration configuration = new TemplateAutoConfiguration();
        TemplateFeatureProperties template = new TemplateFeatureProperties();
        template.setPersistence(PersistenceProperties.Type.jpa);
        PersistenceProperties persistence = new PersistenceProperties(PersistenceProperties.Type.mybatis);
        TemplateJpaPersistenceRepository jpa = new TemplateJpaPersistenceRepository(stub(TemplateJpaRepository.class));

        TemplatesServiceImpl service = configuration.templatesService(template, persistence,
                provider(jpa),
                provider(new TemplateJdbcRepository(jdbcTemplate("PostgreSQL"))),
                new FreemarkerTemplateBuilder(null, null));

        assertThat((TemplatePersistenceRepository) ReflectionTestUtils.getField(service, "templateRepository"))
                .isSameAs(jpa);
    }

    @Test
    void jdbcRepositoryFailsFastForUnsupportedDatabase() {
        TemplateAutoConfiguration configuration = new TemplateAutoConfiguration();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> configuration.templateJdbcRepository(jdbcTemplate("MySQL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template")
                .hasMessageContaining("PostgreSQL only");
    }

    @Test
    void webConfigBacksOffWhenSecurityWebClassesAreMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        TemplateAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(
                        "org.springframework.security.authentication",
                        "org.springframework.security.access.prepost"))
                .withPropertyValues("studio.features.template.enabled=true")
                .withBean(TemplatesService.class, () -> stub(TemplatesService.class))
                .withBean(FreemarkerTemplateBuilder.class, () -> new FreemarkerTemplateBuilder(null, null))
                .withBean(TemplateJpaPersistenceRepository.class,
                        () -> new TemplateJpaPersistenceRepository(stub(TemplateJpaRepository.class)))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TemplateMgmtController.class);
                });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) {
                        return type.getSimpleName() + "Stub";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public T getObject() {
                return value;
            }
        };
    }

    private static NamedParameterJdbcTemplate jdbcTemplate(String productName) {
        return new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource(productName)));
    }

    private static DataSource dataSource(String productName) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return connection(productName);
                    }
                    if ("toString".equals(method.getName())) {
                        return "DataSourceStub";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Connection connection(String productName) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) {
                        return databaseMetaData(productName);
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static DatabaseMetaData databaseMetaData(String productName) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    if ("getDatabaseProductName".equals(method.getName())) {
                        return productName;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
