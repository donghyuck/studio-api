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
        requireDatabaseProduct(dataSource, featureName + " JDBC persistence", "PostgreSQL");
    }

    public static void requireDatabaseProduct(DataSource dataSource, String featureName, String... supportedProducts) {
        String productName = databaseProductName(dataSource, featureName);
        if (!isSupported(productName, supportedProducts)) {
            throw new IllegalStateException(featureName + " supports " + String.join(", ", supportedProducts)
                    + " only. Detected database: " + productName
                    + ". Provide a database-specific implementation before enabling this feature.");
        }
    }

    public static String databaseProductName(DataSource dataSource, String featureName) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect database product for " + featureName, ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean isSupported(String productName, String... supportedProducts) {
        if (productName == null) {
            return false;
        }
        String normalized = productName.toLowerCase(Locale.ROOT);
        for (String supportedProduct : supportedProducts) {
            if (supportedProduct != null && normalized.contains(supportedProduct.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
