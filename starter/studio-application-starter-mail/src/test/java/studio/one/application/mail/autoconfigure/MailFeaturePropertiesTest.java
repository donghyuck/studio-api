package studio.one.application.mail.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import studio.one.application.mail.persistence.repository.MailMessageRepository;
import studio.one.application.mail.service.impl.JdbcMailMessageService;
import studio.one.application.mail.service.impl.JpaMailMessageService;
import studio.one.platform.autoconfigure.PersistenceProperties;

class MailFeaturePropertiesTest {

    @Test
    void featurePropertyResolverLeavesGlobalMyBatisRaw() {
        MailFeatureProperties properties = new MailFeatureProperties();

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.mybatis));
    }

    @Test
    void featurePropertyResolverLeavesExplicitMyBatisRaw() {
        MailFeatureProperties properties = new MailFeatureProperties();
        properties.setPersistence(PersistenceProperties.Type.mybatis);

        assertEquals(PersistenceProperties.Type.mybatis,
                properties.resolvePersistence(PersistenceProperties.Type.jpa));
    }

    @Test
    void globalMyBatisSelectsJdbcCompatibilityService() {
        MailAutoConfiguration configuration = new MailAutoConfiguration();
        MailFeatureProperties mail = new MailFeatureProperties();
        PersistenceProperties persistence = new PersistenceProperties(PersistenceProperties.Type.mybatis);

        assertThat(configuration.mailMessageService(mail, persistence,
                provider(stub(MailMessageRepository.class)),
                provider(jdbcTemplate())))
                .isInstanceOf(JdbcMailMessageService.class);
    }

    @Test
    void explicitJpaOverrideSelectsJpaService() {
        MailAutoConfiguration configuration = new MailAutoConfiguration();
        MailFeatureProperties mail = new MailFeatureProperties();
        mail.setPersistence(PersistenceProperties.Type.jpa);
        PersistenceProperties persistence = new PersistenceProperties(PersistenceProperties.Type.mybatis);

        assertThat(configuration.mailMessageService(mail, persistence,
                provider(stub(MailMessageRepository.class)),
                provider(jdbcTemplate())))
                .isInstanceOf(JpaMailMessageService.class);
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
