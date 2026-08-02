package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.jupiter.api.Test;

class WebKnowledgeRevisionEntityMappingTest {

    @Test
    void mapsLargeTextFieldsToDialectNativeTextTypes() {
        assertLargeTextColumns(PostgreSQLDialect.class, "text");
        assertLargeTextColumns(MySQLDialect.class, "longtext");
        assertLargeTextColumns(MariaDBDialect.class, "longtext");
    }

    @Test
    void mapsCrawlQuotaFieldsToMigrationColumnNames() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(WebKnowledgeCrawlRunEntity.class)
                    .buildMetadata();
            var table = metadata.getDatabase()
                    .getDefaultNamespace()
                    .locateTable(org.hibernate.boot.model.naming.Identifier.toIdentifier("web_knowledge_crawl_run"));

            assertNotNull(table.getColumn(
                    org.hibernate.boot.model.naming.Identifier.toIdentifier("reserved_page_count")));
            assertNotNull(table.getColumn(
                    org.hibernate.boot.model.naming.Identifier.toIdentifier("reserved_snapshot_bytes")));
            assertNotNull(table.getColumn(
                    org.hibernate.boot.model.naming.Identifier.toIdentifier("quota_released_at")));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void mapsQuotaUsageFieldsToMigrationColumnNames() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", PostgreSQLDialect.class.getName())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(WebKnowledgeQuotaUsageEntity.class)
                    .buildMetadata();
            var table = metadata.getDatabase()
                    .getDefaultNamespace()
                    .locateTable(org.hibernate.boot.model.naming.Identifier.toIdentifier("web_knowledge_quota_usage"));

            for (String column : new String[] {
                    "workspace_id",
                    "source_count",
                    "active_page_count",
                    "normalized_snapshot_bytes",
                    "reserved_page_count",
                    "reserved_snapshot_bytes",
                    "updated_at",
                    "lock_version"
            }) {
                assertNotNull(table.getColumn(
                        org.hibernate.boot.model.naming.Identifier.toIdentifier(column)),
                        column);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertLargeTextColumns(Class<?> dialect, String expectedType) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", dialect.getName())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(WebKnowledgeRevisionEntity.class)
                    .buildMetadata();
            var table = metadata.getDatabase()
                    .getDefaultNamespace()
                    .locateTable(org.hibernate.boot.model.naming.Identifier.toIdentifier("web_knowledge_revision"));

            assertEquals(expectedType, table
                    .getColumn(org.hibernate.boot.model.naming.Identifier.toIdentifier("normalized_snapshot"))
                    .getSqlType(metadata));
            assertEquals(expectedType, table
                    .getColumn(org.hibernate.boot.model.naming.Identifier.toIdentifier("metadata_json"))
                    .getSqlType(metadata));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
