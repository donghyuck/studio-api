package studio.one.platform.data.jdbc.pagination;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaginationDialectResolver {

    public PaginationDialect resolve(DataSource dataSource) {
        try (Connection con = dataSource.getConnection()) {
            DatabaseMetaData metaData = con.getMetaData();
            String productName = metaData.getDatabaseProductName();
            String lower = productName.toLowerCase();
            log.debug("Resolving PaginationDialect for database: {}", productName);
            if (lower.contains("mysql") || lower.contains("mariadb")) {
                return new MySqlPaginationDialect();
            } else if (lower.contains("postgresql") || lower.contains("postgres")) {
                return new PostgresPaginationDialect();
            } else if (lower.contains("oracle")) {
                return new OraclePaginationDialect();
            } else if (lower.contains("sql server")) {
                return new SqlServerPaginationDialect();
            } else {
                log.warn("Unknown database type for pagination: {}", productName);
                return new DefaultPaginationDialect();
            }

        } catch (SQLException e) {
            log.error("Failed to resolve PaginationDialect. Fallback to Default.", e);
            return new DefaultPaginationDialect();
        }
    }
}