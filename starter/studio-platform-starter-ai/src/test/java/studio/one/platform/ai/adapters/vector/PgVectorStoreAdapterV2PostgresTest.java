package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;

@Testcontainers
class PgVectorStoreAdapterV2PostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private PgVectorStoreAdapterV2 adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("DROP TABLE IF EXISTS tb_ai_document_chunk");
        jdbcTemplate.execute(String.join("\n",
        "CREATE TABLE tb_ai_document_chunk (",
        "    id BIGSERIAL PRIMARY KEY,",
        "    object_type VARCHAR(100) NOT NULL,",
        "    object_id VARCHAR(100) NOT NULL,",
        "    chunk_index INTEGER NOT NULL,",
        "    text TEXT NOT NULL,",
        "    metadata JSONB NOT NULL,",
        "    embedding vector(2) NOT NULL,",
        "    CONSTRAINT uq_test_chunk UNIQUE (object_type, object_id, chunk_index)",
        ")"));
        PgVectorMapper mapper = mapper(dataSource);
        adapter = new PgVectorStoreAdapterV2(mapper, dataSource);
        adapter.upsert(List.of(
                document("chunk-1", "attachment", "6", 0, "java backend", List.of(0.1, 0.2),
                        Map.of("topic", "backend", "embeddingInputType", "TEXT")),
                document("chunk-2", "forums-post-attachment", "7", 0, "spring api", List.of(0.2, 0.3),
                        Map.of("topic", "api", "embeddingInputType", "TABLE_TEXT"))));
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

    @Test
    void searchAppliesMetadataEqualsAndInCriteriaThroughMyBatis() {
        MetadataFilter filter = MetadataFilter.of(
                Map.of("topic", "backend"),
                Map.of("embeddingInputType", List.of("TEXT")),
                Map.of());

        List<VectorSearchResult> results = adapter.search(new VectorSearchRequest(List.of(0.1, 0.2), 10, filter));

        assertThat(results).singleElement()
                .extracting(result -> result.document().id())
                .isEqualTo("chunk-1");
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyJdbcConstructorUsesDirectJdbcFallbackMapper() {
        PgVectorStoreAdapterV2 jdbcAdapter = new PgVectorStoreAdapterV2(jdbcTemplate);

        List<VectorSearchResult> results = jdbcAdapter.searchByObject(
                "attachment",
                null,
                new VectorSearchRequest(List.of(0.1, 0.2), 10));

        assertThat(results).singleElement()
                .extracting(result -> result.document().id())
                .isEqualTo("chunk-1");
    }

    private static PgVectorMapper mapper(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/ai/PgVectorMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        return new SqlSessionTemplate(factory).getMapper(PgVectorMapper.class);
    }

    private static VectorDocument document(
            String id,
            String objectType,
            String objectId,
            int chunkIndex,
            String text,
            List<Double> embedding,
            Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("objectType", objectType);
        metadata.put("objectId", objectId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("chunkId", id);
        metadata.putAll(extraMetadata);
        return new VectorDocument(id, text, metadata, embedding);
    }
}
