package studio.one.platform.autoconfigure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcAutoConfiguration.class))
            .withBean(DataSource.class, this::dataSource)
            .withPropertyValues("studio.persistence.jdbc.enabled=true");

    @Test
    void loadsJdbcAutoConfigurationFromPersistencePackage() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JdbcTemplate.class);
        });
    }

    private DataSource dataSource() {
        return new org.springframework.jdbc.datasource.AbstractDataSource() {
            @Override
            public java.sql.Connection getConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.sql.Connection getConnection(String username, String password) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
