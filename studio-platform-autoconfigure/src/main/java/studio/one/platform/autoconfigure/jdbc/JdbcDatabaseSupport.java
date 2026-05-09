package studio.one.platform.autoconfigure.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

public final class JdbcDatabaseSupport {

    private JdbcDatabaseSupport() {
    }

    public static void requirePostgreSQL(NamedParameterJdbcTemplate template, String featureName) {
        DataSource dataSource = template.getJdbcTemplate().getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException(featureName + " JDBC persistence requires a DataSource.");
        }
        requirePostgreSQL(dataSource, featureName);
    }

    public static void requirePostgreSQL(DataSource dataSource, String featureName) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (!isPostgreSQL(productName)) {
                throw new IllegalStateException(featureName
                        + " JDBC persistence supports PostgreSQL only. Detected database: " + productName
                        + ". Provide a database-specific implementation before enabling this feature.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect database product for JDBC persistence: " + featureName,
                    ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean isPostgreSQL(String productName) {
        return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
    }
}
