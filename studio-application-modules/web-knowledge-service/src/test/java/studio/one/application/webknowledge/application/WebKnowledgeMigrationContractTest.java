package studio.one.application.webknowledge.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebKnowledgeMigrationContractTest {

    @Test
    void allSupportedDatabasesContainTheBoundedCrawlSchemaAndLookupIndexes() throws IOException {
        for (String database : List.of("postgres", "mysql", "mariadb")) {
            String bounded = migration(database, "V1710__add_bounded_site_crawl.sql");
            String indexes = migration(database, "V1713__add_crawl_lookup_indexes.sql");

            assertTrue(bounded.contains("web_knowledge_crawl_run"), database);
            assertTrue(bounded.contains("web_knowledge_crawl_item"), database);
            assertTrue(bounded.contains("web_knowledge_page_revision"), database);
            assertTrue(bounded.contains("web_knowledge_corpus_revision"), database);
            assertTrue(bounded.contains("web_knowledge_quota_usage"), database);
            assertTrue(bounded.contains("current_corpus_revision_id"), database);
            assertTrue(indexes.contains("canonical_url_hash"), database);
            assertTrue(indexes.contains("content_hash"), database);
        }
    }

    @Test
    void legacyBackfillUsesBoundedDeterministicIdsAndIsIdempotent() throws IOException {
        for (String database : List.of("postgres", "mysql", "mariadb")) {
            String backfill = migration(database, "V1712__backfill_single_page_corpus.sql");

            assertTrue(backfill.toLowerCase().contains("md5("), database);
            assertTrue(backfill.contains("legacy-corpus-"), database);
            assertTrue(database.equals("postgres")
                    ? backfill.contains("ON CONFLICT")
                    : backfill.contains("INSERT IGNORE"), database);
            assertFalse(backfill.contains("'legacy-corpus-' || source.current_revision_id"), database);
            assertFalse(backfill.contains(
                    "CONCAT('legacy-corpus-', web_knowledge_source.current_revision_id)"), database);
        }
    }

    private static String migration(String database, String name) throws IOException {
        String path = "/schema/web-knowledge/" + database + "/" + name;
        try (var stream = WebKnowledgeMigrationContractTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
