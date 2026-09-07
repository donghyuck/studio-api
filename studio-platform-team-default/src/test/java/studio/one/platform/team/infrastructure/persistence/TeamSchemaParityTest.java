package studio.one.platform.team.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class TeamSchemaParityTest {

    @Test
    void allSupportedDialectsCreateTheTeamBoundary() throws Exception {
        for (String dialect : new String[] { "postgres", "mysql", "mariadb" }) {
            String resource = "/schema/team/" + dialect + "/V1800__create_team_tables.sql";
            try (var stream = getClass().getResourceAsStream(resource)) {
                assertThat(stream).as(resource).isNotNull();
                String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toUpperCase();
                assertThat(sql)
                        .contains("TB_PLATFORM_TEAM")
                        .contains("COMPANY_ID")
                        .contains("PERMISSION_VERSION")
                        .contains("TB_PLATFORM_TEAM_MEMBER")
                        .contains("RAG_REPLY_MODE");
            }
        }
    }

    @Test
    void allSupportedDialectsAllowCompanylessTeamsAndRejectInvalidAssignments() throws Exception {
        assertSchemaBehavior("PostgreSQL", "postgres");
        assertSchemaBehavior("MySQL", "mysql");
        assertSchemaBehavior("MySQL", "mariadb");
    }

    @Test
    void allSupportedDialectsCreateIdempotentSingleFlightMigrationTables() throws Exception {
        assertThat(readResource("postgres", "V1802__create_team_migration_tables.sql"))
                .contains("WORKSPACE_SNAPSHOT_REF TEXT")
                .contains("KNOWLEDGE_SNAPSHOT_REF TEXT");
        assertThat(readResource("mysql", "V1802__create_team_migration_tables.sql"))
                .contains("WORKSPACE_SNAPSHOT_REF LONGTEXT")
                .contains("KNOWLEDGE_SNAPSHOT_REF LONGTEXT");
        assertThat(readResource("mariadb", "V1802__create_team_migration_tables.sql"))
                .contains("WORKSPACE_SNAPSHOT_REF LONGTEXT")
                .contains("KNOWLEDGE_SNAPSHOT_REF LONGTEXT");
        assertMigrationSchemaBehavior("PostgreSQL", "postgres");
        assertMigrationSchemaBehavior("MySQL", "mysql");
        assertMigrationSchemaBehavior("MySQL", "mariadb");
    }

    @Test
    void allSupportedDialectsCreateOneJoinRequestPerTeamMember() throws Exception {
        assertJoinRequestSchemaBehavior("PostgreSQL", "postgres");
        assertJoinRequestSchemaBehavior("MySQL", "mysql");
        assertJoinRequestSchemaBehavior("MySQL", "mariadb");
    }

    private void assertSchemaBehavior(String h2Mode, String dialect) throws Exception {
        String resource = "/schema/team/" + dialect + "/V1800__create_team_tables.sql";
        String sql;
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if ("postgres".equals(dialect)) {
            sql = sql.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
        }
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:team_" + dialect + "_" + System.nanoTime()
                        + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                var statement = connection.createStatement()) {
            statement.execute("create table TB_APPLICATION_COMPANY (COMPANY_ID BIGINT primary key)");
            for (String fragment : sql.split(";")) {
                if (!fragment.isBlank()) {
                    statement.execute(fragment);
                }
            }
            statement.execute("""
                    insert into TB_PLATFORM_TEAM
                        (COMPANY_ID, NAME, SLUG, CREATED_BY, UPDATED_BY)
                    values (null, 'Public Team', 'public-team', 1, 1)
                    """);
            statement.execute("insert into TB_APPLICATION_COMPANY (COMPANY_ID) values (10)");
            statement.execute("""
                    insert into TB_PLATFORM_TEAM
                        (COMPANY_ID, NAME, SLUG, CREATED_BY, UPDATED_BY)
                    values (10, 'Company Team', 'company-team', 1, 1)
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_TEAM
                        (COMPANY_ID, NAME, SLUG, CREATED_BY, UPDATED_BY)
                    values (999, 'Invalid Team', 'invalid-team', 1, 1)
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertMigrationSchemaBehavior(String h2Mode, String dialect) throws Exception {
        String databaseName = "team_migration_" + dialect + "_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                var statement = connection.createStatement()) {
            statement.execute("create table TB_APPLICATION_COMPANY (COMPANY_ID BIGINT primary key)");
            executeResource(statement, dialect, "V1800__create_team_tables.sql");
            executeResource(statement, dialect, "V1802__create_team_migration_tables.sql");
            statement.execute("""
                    insert into TB_PLATFORM_TEAM
                        (TEAM_ID, NAME, SLUG, CREATED_BY, UPDATED_BY)
                    values (7, 'Target', 'target', 1, 1)
                    """);
            statement.execute("""
                    insert into TB_PLATFORM_TEAM_MIGRATION_RUN
                        (RUN_ID, IDEMPOTENCY_KEY, TARGET_TEAM_ID, SOURCE_ROOT_IDS, CREATED_BY, UPDATED_BY)
                    values ('00000000-0000-0000-0000-000000000001', 'key-1', 7, '11', 1, 1)
                    """);
            statement.execute("""
                    insert into TB_PLATFORM_TEAM_MIGRATION_LOCK (LOCK_KEY, RUN_ID)
                    values ('root:11', '00000000-0000-0000-0000-000000000001')
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_TEAM_MIGRATION_RUN
                        (RUN_ID, IDEMPOTENCY_KEY, TARGET_TEAM_ID, SOURCE_ROOT_IDS, CREATED_BY, UPDATED_BY)
                    values ('00000000-0000-0000-0000-000000000002', 'key-1', 7, '12', 1, 1)
                    """))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_TEAM_MIGRATION_LOCK (LOCK_KEY, RUN_ID)
                    values ('root:11', '00000000-0000-0000-0000-000000000001')
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertJoinRequestSchemaBehavior(String h2Mode, String dialect) throws Exception {
        String databaseName = "team_join_" + dialect + "_" + System.nanoTime();
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName + ";MODE=" + h2Mode + ";DB_CLOSE_DELAY=-1");
                var statement = connection.createStatement()) {
            statement.execute("create table TB_APPLICATION_COMPANY (COMPANY_ID BIGINT primary key)");
            executeResource(statement, dialect, "V1800__create_team_tables.sql");
            executeResource(statement, dialect, "V1803__create_team_join_request.sql");
            statement.execute("""
                    insert into TB_PLATFORM_TEAM
                        (TEAM_ID, NAME, SLUG, CREATED_BY, UPDATED_BY)
                    values (7, 'Target', 'join-target', 1, 1)
                    """);
            statement.execute("""
                    insert into TB_PLATFORM_TEAM_JOIN_REQUEST (TEAM_ID, USER_ID, STATUS)
                    values (7, 20, 'PENDING')
                    """);
            assertThatThrownBy(() -> statement.execute("""
                    insert into TB_PLATFORM_TEAM_JOIN_REQUEST (TEAM_ID, USER_ID, STATUS)
                    values (7, 20, 'PENDING')
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void executeResource(java.sql.Statement statement, String dialect, String file) throws Exception {
        String sql = readResource(dialect, file);
        if ("postgres".equals(dialect)) {
            sql = sql.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
        }
        for (String fragment : sql.split(";")) {
            if (!fragment.isBlank()) {
                statement.execute(fragment);
            }
        }
    }

    private String readResource(String dialect, String file) throws Exception {
        String resource = "/schema/team/" + dialect + "/" + file;
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
