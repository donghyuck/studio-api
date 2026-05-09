package studio.one.application.template.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import studio.one.application.template.persistence.TemplatePersistenceRepository;
import studio.one.application.template.persistence.jdbc.TemplateJdbcRepository;
import studio.one.application.template.persistence.jpa.repo.TemplateJpaPersistenceRepository;
import studio.one.application.template.persistence.jpa.repo.TemplateJpaRepository;
import studio.one.application.template.service.impl.FreemarkerTemplateBuilder;
import studio.one.application.template.service.impl.TemplatesServiceImpl;
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
                provider(new TemplateJdbcRepository(jdbcTemplate())),
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
                provider(new TemplateJdbcRepository(jdbcTemplate())),
                new FreemarkerTemplateBuilder(null, null));

        assertThat((TemplatePersistenceRepository) ReflectionTestUtils.getField(service, "templateRepository"))
                .isSameAs(jpa);
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

    private static NamedParameterJdbcTemplate jdbcTemplate() {
        return new NamedParameterJdbcTemplate(
                new JdbcTemplate(new DriverManagerDataSource("jdbc:unused", "sa", "")));
    }
}
