package studio.one.platform.ai.adapters.vector;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgVectorStoreAdapterTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("test")
            .withUsername("postgres")
            .withPassword("postgres");

    private PgVectorStoreAdapter adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.adapter = new PgVectorStoreAdapter(jdbcTemplate);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_documents (" +
                "id varchar(255) PRIMARY KEY, " +
                "content text NOT NULL, " +
                "metadata jsonb, " +
                "embedding vector NOT NULL)");
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE ai_documents");
    }

    @Test
    void shouldUpsertAndSearchDocuments() {
        VectorDocument doc1 = new VectorDocument("doc-1", "hello world", Map.of("topic", "greeting"), List.of(0.1, 0.2, 0.3));
        VectorDocument doc2 = new VectorDocument("doc-2", "spring boot", Map.of("topic", "framework"), List.of(0.9, 0.8, 0.7));

        adapter.upsert(List.of(doc1, doc2));

        VectorSearchRequest request = new VectorSearchRequest(List.of(0.12, 0.19, 0.31), 1);
        List<VectorSearchResult> results = adapter.search(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-1");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "greeting");
        assertThat(results.get(0).score()).isGreaterThan(0.0d);
    }
}
