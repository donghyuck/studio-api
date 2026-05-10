package studio.one.application.mail.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.application.mail.infrastructure.persistence.jpa.MailMessageRepository;
import studio.one.application.mail.application.service.JdbcMailMessageService;
import studio.one.application.mail.application.service.JpaMailMessageService;
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
                provider(jdbcTemplate("PostgreSQL"))))
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

    @Test
    void jdbcCompatibilityServiceFailsFastForUnsupportedDatabase() {
        MailAutoConfiguration configuration = new MailAutoConfiguration();
        MailFeatureProperties mail = new MailFeatureProperties();
        PersistenceProperties persistence = new PersistenceProperties(PersistenceProperties.Type.mybatis);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> configuration.mailMessageService(mail,
                persistence,
                provider(stub(MailMessageRepository.class)),
                provider(jdbcTemplate("MySQL")))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mail message")
                .hasMessageContaining("PostgreSQL only");
    }

    private static NamedParameterJdbcTemplate jdbcTemplate() {
        return jdbcTemplate("PostgreSQL");
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
