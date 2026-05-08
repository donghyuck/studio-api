/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file SecurityJdbcDatabaseSupport.java
 *      @date 2026
 *
 */

package studio.one.platform.security.autoconfigure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

final class SecurityJdbcDatabaseSupport {

    private SecurityJdbcDatabaseSupport() {
    }

    static void requirePostgreSQL(NamedParameterJdbcTemplate template, String featureName) {
        DataSource dataSource = template.getJdbcTemplate().getDataSource();
        if (dataSource == null) {
            throw new IllegalStateException(
                    "Security JDBC persistence for " + featureName + " requires a DataSource.");
        }
        requirePostgreSQL(dataSource, featureName, "Security JDBC persistence");
    }

    static void requirePostgreSQL(DataSource dataSource, String featureName) {
        requirePostgreSQL(dataSource, featureName, "Security persistence");
    }

    private static void requirePostgreSQL(DataSource dataSource, String featureName, String persistenceName) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if (!isPostgreSQL(productName)) {
                throw new IllegalStateException(persistenceName + " for " + featureName
                        + " supports PostgreSQL only. Detected database: " + productName
                        + ". Provide a database-specific implementation before enabling this feature.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Failed to inspect database product for security JDBC persistence: " + featureName, ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean isPostgreSQL(String productName) {
        return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
    }
}
