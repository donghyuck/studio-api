package studio.one.application.avatar.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import studio.one.application.avatar.application.usecase.AvatarImageService;
import studio.one.application.avatar.domain.port.AvatarImageDataRepository;
import studio.one.application.avatar.domain.port.AvatarImageRepository;
import studio.one.application.avatar.infrastructure.persistence.jdbc.AvatarImageDataJdbcRepository;
import studio.one.application.avatar.infrastructure.persistence.jdbc.AvatarImageJdbcRepository;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.ConfigRoot;
import studio.one.platform.service.Repository;

class AvatarAutoConfigurationTest {

    @TempDir
    File tempDir;

    @Test
    void jdbcPersistenceRegistersRepositoriesAndService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        AvatarAutoConfiguration.class))
                .withPropertyValues(
                        "studio.features.avatar-image.enabled=true",
                        "studio.features.avatar-image.persistence=jdbc",
                        "studio.features.avatar-image.replica.base-dir=" + tempDir.getAbsolutePath(),
                        "studio.persistence.type=jdbc")
                .withBean(ServiceNames.NAMED_JDBC_TEMPLATE, NamedParameterJdbcTemplate.class,
                        () -> new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource())))
                .withBean(Repository.class, () -> repository(tempDir))
                .run(context -> {
                    assertThat(context).hasSingleBean(AvatarImageRepository.class);
                    assertThat(context).hasSingleBean(AvatarImageDataRepository.class);
                    assertThat(context).hasBean(AvatarImageService.SERVICE_NAME);
                    assertThat(context.getBean(AvatarImageRepository.class))
                            .isInstanceOf(AvatarImageJdbcRepository.class);
                    assertThat(context.getBean(AvatarImageDataRepository.class))
                            .isInstanceOf(AvatarImageDataJdbcRepository.class);
                });
    }

    @Test
    void jpaPersistenceRegistersEntityScanWithProvidedRepositoryPorts() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        AvatarAutoConfiguration.class))
                .withPropertyValues(
                        "studio.features.avatar-image.enabled=true",
                        "studio.features.avatar-image.persistence=jpa",
                        "studio.features.avatar-image.replica.base-dir=" + tempDir.getAbsolutePath(),
                        "studio.persistence.type=jpa")
                .withBean(AvatarImageRepository.class, () -> stub(AvatarImageRepository.class))
                .withBean(AvatarImageDataRepository.class, () -> stub(AvatarImageDataRepository.class))
                .withBean(Repository.class, () -> repository(tempDir))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("avatarEntityScanRegistrar");
                    assertThat(context).hasBean(AvatarImageService.SERVICE_NAME);
                });
    }

    private static DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return connection();
                    }
                    if ("toString".equals(method.getName())) {
                        return "DataSourceStub";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Connection connection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("getMetaData".equals(method.getName())) {
                        return databaseMetaData();
                    }
                    if ("close".equals(method.getName())) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static DatabaseMetaData databaseMetaData() {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class },
                (proxy, method, args) -> {
                    if ("getDatabaseProductName".equals(method.getName())) {
                        return "PostgreSQL";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Repository repository(File root) {
        return new Repository() {
            @Override
            public ConfigRoot getConfigRoot() {
                throw new UnsupportedOperationException("getConfigRoot");
            }

            @Override
            public File getFile(String name) {
                return new File(root, name);
            }

            @Override
            public ApplicationProperties getApplicationProperties() {
                throw new UnsupportedOperationException("getApplicationProperties");
            }

            @Override
            public Duration getUptime() {
                return Duration.ZERO;
            }
        };
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
}
