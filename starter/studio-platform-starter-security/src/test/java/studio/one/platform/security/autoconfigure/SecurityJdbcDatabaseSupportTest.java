package studio.one.platform.security.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SecurityJdbcDatabaseSupportTest {

    @Test
    void requirePostgreSQLAllowsPostgreSQL() throws Exception {
        NamedParameterJdbcTemplate template = templateWithProductName("PostgreSQL");

        assertDoesNotThrow(() -> SecurityJdbcDatabaseSupport.requirePostgreSQL(template, "test"));
    }

    @Test
    void requirePostgreSQLRejectsOtherDatabase() throws Exception {
        NamedParameterJdbcTemplate template = templateWithProductName("MySQL");

        assertThrows(IllegalStateException.class,
                () -> SecurityJdbcDatabaseSupport.requirePostgreSQL(template, "test"));
    }

    @Test
    void requirePostgreSQLRejectsOtherDatabaseFromDataSource() throws Exception {
        DataSource dataSource = dataSourceWithProductName("MariaDB");

        assertThrows(IllegalStateException.class,
                () -> SecurityJdbcDatabaseSupport.requirePostgreSQL(dataSource, "login failure audit"));
    }

    private NamedParameterJdbcTemplate templateWithProductName(String productName) throws Exception {
        DataSource dataSource = dataSourceWithProductName(productName);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        NamedParameterJdbcTemplate template = mock(NamedParameterJdbcTemplate.class);
        when(template.getJdbcTemplate()).thenReturn(jdbcTemplate);
        return template;
    }

    private DataSource dataSourceWithProductName(String productName) throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metaData);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
