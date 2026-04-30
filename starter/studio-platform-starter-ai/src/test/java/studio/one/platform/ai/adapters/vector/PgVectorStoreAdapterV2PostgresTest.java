package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.w3c.dom.Element;

import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;

@Testcontainers
class PgVectorStoreAdapterV2PostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private PgVectorStoreAdapterV2 adapter;

    @BeforeEach
    void setUp() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("DROP TABLE IF EXISTS tb_ai_document_chunk");
        jdbcTemplate.execute("""
                CREATE TABLE tb_ai_document_chunk (
                    id BIGSERIAL PRIMARY KEY,
                    object_type VARCHAR(100) NOT NULL,
                    object_id VARCHAR(100) NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    metadata JSONB NOT NULL,
                    embedding vector(2) NOT NULL,
                    CONSTRAINT uq_test_chunk UNIQUE (object_type, object_id, chunk_index)
                )
                """);
        adapter = new PgVectorStoreAdapterV2(jdbcTemplate);
        setField("upsertSql", sql("upsertChunk"));
        setField("searchByObjectSql", sql("searchByObject"));
        setField("hybridSearchByObjectSql", sql("hybridSearchByObject"));
        adapter.upsert(List.of(
                document("chunk-1", "attachment", "6", 0, "java backend", List.of(0.1, 0.2)),
                document("chunk-2", "forums-post-attachment", "7", 0, "spring api", List.of(0.2, 0.3))));
    }

    @Test
    void searchByObjectAllowsObjectTypeOnlyScopeWithNullObjectId() {
        List<VectorSearchResult> results = adapter.searchByObject(
                "attachment",
                null,
                new VectorSearchRequest(List.of(0.1, 0.2), 10));

        assertThat(results).singleElement()
                .extracting(result -> result.document().id())
                .isEqualTo("chunk-1");
    }

    @Test
    void hybridSearchByObjectAllowsObjectTypeOnlyScopeWithNullObjectId() {
        List<VectorSearchResult> results = adapter.hybridSearchByObject(
                "java",
                "attachment",
                null,
                new VectorSearchRequest(List.of(0.1, 0.2), 10),
                0.7,
                0.3);

        assertThat(results).singleElement()
                .extracting(result -> result.document().id())
                .isEqualTo("chunk-1");
    }

    private static VectorDocument document(
            String id,
            String objectType,
            String objectId,
            int chunkIndex,
            String text,
            List<Double> embedding) {
        return new VectorDocument(id, text, Map.of(
                "objectType", objectType,
                "objectId", objectId,
                "chunkIndex", chunkIndex,
                "chunkId", id), embedding);
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = PgVectorStoreAdapterV2.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(adapter, value);
    }

    private static String sql(String id) throws Exception {
        try (var input = PgVectorStoreAdapterV2PostgresTest.class.getClassLoader()
                .getResourceAsStream("sql/ai-sqlset.xml")) {
            var document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(Objects.requireNonNull(input).readAllBytes()));
            var nodes = document.getElementsByTagName("sql-query");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                if (id.equals(element.getAttribute("id"))) {
                    return element.getTextContent().trim();
                }
            }
        }
        throw new IllegalArgumentException("SQL not found: " + id);
    }
}
