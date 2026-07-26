package studio.one.platform.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class DatabaseSchemaCompatibilityTest {

    private static final String[] SCHEMA_DOMAINS = {
            "data",
            "objecttype",
            "user",
            "security",
            "security-acl",
            "ai",
            "workspace",
            "avatar",
            "attachment",
            "template",
            "mail",
            "wiki",
            "skillgraph",
            "document-convert",
            "markdown"
    };

    @Test
    void appliesAllPostgresMigrationsInVersionOrder() throws Exception {
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres");
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(image)) {
            postgres.start();
            try (var connection = DriverManager.getConnection(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            }
            applyMigrations("postgres", postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }
    }

    @Test
    void appliesAllMysqlMigrationsInVersionOrder() throws Exception {
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            applyMigrations("mysql", mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
    }

    @Test
    void appliesAllMariadbMigrationsInVersionOrder() throws Exception {
        try (MariaDBContainer mariadb = new MariaDBContainer("mariadb:11.4")) {
            mariadb.start();
            applyMigrations("mariadb", mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
        }
    }

    private void applyMigrations(String dialect, String jdbcUrl, String username, String password) {
        String[] locations = java.util.Arrays.stream(SCHEMA_DOMAINS)
                .map(domain -> "classpath:schema/" + domain + "/" + dialect)
                .toArray(String[]::new);
        var result = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(locations)
                .failOnMissingLocations(false)
                .validateMigrationNaming(true)
                .load()
                .migrate();
        assertThat(result.migrationsExecuted)
                .as("executed migrations for %s", dialect)
                .isPositive();
    }
}
