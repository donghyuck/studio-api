package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.pgvector.PGvector;

import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;

class PgVectorStoreAdapterV2Test {

    private static final String UPSERT_SQL = "upsert-sql";
    private static final String SEARCH_SQL = "search-sql";
    private static final String DELETE_BY_OBJECT_SQL = "delete-by-object-sql";
    private static final String SEARCH_BY_OBJECT_SQL = "search-by-object-sql";
    private static final String HYBRID_SEARCH_SQL = "hybrid-search-sql";
    private static final String LIST_BY_OBJECT_SQL = "list-by-object-sql";
    private static final String METADATA_BY_OBJECT_SQL = "metadata-by-object-sql";

    private JdbcTemplate jdbcTemplate;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private PgVectorStoreAdapterV2 adapter;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        adapter = new PgVectorStoreAdapterV2(jdbcTemplate);
        setField("namedParameterJdbcTemplate", namedParameterJdbcTemplate);
        setField("upsertSql", UPSERT_SQL);
        setField("searchSql", SEARCH_SQL);
        setField("deleteByObjectSql", DELETE_BY_OBJECT_SQL);
        setField("searchByObjectSql", SEARCH_BY_OBJECT_SQL);
        setField("hybridSearchSql", HYBRID_SEARCH_SQL);
        setField("listByObjectSql", LIST_BY_OBJECT_SQL);
        setField("metadataByObjectSql", METADATA_BY_OBJECT_SQL);
    }

    @Test
    void upsertBindsSqlSetParametersForActiveAdapterPath() {
        VectorDocument document = new VectorDocument(
                "doc-1",
                "hello world",
                Map.of("objectType", "ARTICLE", "objectId", "article-1", "chunkOrder", 3, "topic", "greeting"),
                List.of(0.1d, 0.2d, 0.3d));

        adapter.upsert(List.of(document));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(namedParameterJdbcTemplate).batchUpdate(anyString(), batchCaptor.capture());
        verify(namedParameterJdbcTemplate).batchUpdate(UPSERT_SQL, batchCaptor.getValue());
        MapSqlParameterSource[] batch = batchCaptor.getValue();
        assertThat(batch).hasSize(1);
        MapSqlParameterSource params = batch[0];
        assertThat(params.getValue("objectType")).isEqualTo("ARTICLE");
        assertThat(params.getValue("objectId")).isEqualTo("article-1");
        assertThat(params.getValue("chunkIndex")).isEqualTo(3);
        assertThat(params.getValue("text")).isEqualTo("hello world");
        assertThat(String.valueOf(params.getValue("metadata"))).contains("\"documentId\":\"doc-1\"");
        assertThat(params.getValue("embedding")).isInstanceOf(PGvector.class);
    }

    @Test
    void upsertRecordAdaptsChunkIndexForPgVectorBinding() {
        VectorRecord record = VectorRecord.builder()
                .id("record-1")
                .documentId("doc-1")
                .chunkId("chunk-1")
                .contentHash("hash-1")
                .text("record text")
                .embedding(List.of(0.1d, 0.2d))
                .embeddingModel("test-embedding")
                .metadata(Map.of(
                        VectorRecord.KEY_OBJECT_TYPE, "ARTICLE",
                        VectorRecord.KEY_OBJECT_ID, "article-1",
                        VectorRecord.KEY_CHUNK_INDEX, 7))
                .build();

        adapter.upsert(record);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(namedParameterJdbcTemplate).batchUpdate(org.mockito.Mockito.eq(UPSERT_SQL), batchCaptor.capture());
        MapSqlParameterSource params = batchCaptor.getValue()[0];
        assertThat(params.getValue("objectType")).isEqualTo("ARTICLE");
        assertThat(params.getValue("objectId")).isEqualTo("article-1");
        assertThat(params.getValue("chunkIndex")).isEqualTo(7);
        assertThat(String.valueOf(params.getValue("metadata"))).contains("\"chunkOrder\":7");
    }

    @Test
    void searchUsesSqlSetQueryAndMapsMetadataDocumentId() throws SQLException {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource params = invocation.getArgument(1);
                    @SuppressWarnings("unchecked")
                    RowMapper<VectorSearchResult> rowMapper = invocation.getArgument(2);
                    assertThat(params.getValue("limit")).isEqualTo(3);
                    assertThat(params.getValue("vector")).isInstanceOf(PGvector.class);

                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("object_id")).thenReturn("object-1");
                    when(rs.getString("text")).thenReturn("stored chunk");
                    when(rs.getString("metadata")).thenReturn("{\"documentId\":\"doc-42\",\"topic\":\"alpha\"}");
                    when(rs.getDouble("distance")).thenReturn(0.5d);
                    return List.of(rowMapper.mapRow(rs, 0));
                });

        List<VectorSearchResult> results = adapter.search(new VectorSearchRequest(List.of(0.2d, 0.3d), 3));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-42");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "alpha");
        verify(namedParameterJdbcTemplate).query(org.mockito.Mockito.eq(SEARCH_SQL), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void deleteByObjectUsesConfiguredSql() {
        adapter.deleteByObject("ARTICLE", "article-1");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).update(org.mockito.Mockito.eq(DELETE_BY_OBJECT_SQL), paramsCaptor.capture());
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("objectType")).isEqualTo("ARTICLE");
        assertThat(params.getValue("objectId")).isEqualTo("article-1");
    }

    @Test
    void replaceByObjectDeletesThenUpsertsWhenNoTransactionManagerIsAvailable() {
        VectorDocument document = new VectorDocument(
                "doc-replace",
                "replacement",
                Map.of("objectType", "ARTICLE", "objectId", "article-1", "chunkOrder", 0),
                List.of(0.1d, 0.2d));

        adapter.replaceByObject("ARTICLE", "article-1", List.of(document));

        verify(namedParameterJdbcTemplate).update(org.mockito.Mockito.eq(DELETE_BY_OBJECT_SQL), any(MapSqlParameterSource.class));
        verify(namedParameterJdbcTemplate).batchUpdate(org.mockito.Mockito.eq(UPSERT_SQL), any(MapSqlParameterSource[].class));
    }

    @Test
    void replaceRecordsByObjectAdaptsScopeAndChunkIndexForPgVectorBinding() {
        VectorRecord record = VectorRecord.builder()
                .id("record-1")
                .documentId("doc-1")
                .chunkId("chunk-1")
                .contentHash("hash-1")
                .text("record text")
                .embedding(List.of(0.1d, 0.2d))
                .embeddingModel("test-embedding")
                .metadata(Map.of(VectorRecord.KEY_CHUNK_INDEX, 7))
                .build();

        adapter.replaceRecordsByObject("ARTICLE", "article-1", List.of(record));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(namedParameterJdbcTemplate).update(org.mockito.Mockito.eq(DELETE_BY_OBJECT_SQL), any(MapSqlParameterSource.class));
        verify(namedParameterJdbcTemplate).batchUpdate(org.mockito.Mockito.eq(UPSERT_SQL), batchCaptor.capture());
        MapSqlParameterSource params = batchCaptor.getValue()[0];
        assertThat(params.getValue("objectType")).isEqualTo("ARTICLE");
        assertThat(params.getValue("objectId")).isEqualTo("article-1");
        assertThat(params.getValue("chunkIndex")).isEqualTo(7);
        assertThat(String.valueOf(params.getValue("metadata")))
                .contains("\"objectType\":\"ARTICLE\"")
                .contains("\"objectId\":\"article-1\"")
                .contains("\"chunkOrder\":7");
    }

    @Test
    void searchByObjectNormalizesBlankFiltersAndMapsMetadataDocumentId() throws SQLException {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource params = invocation.getArgument(1);
                    @SuppressWarnings("unchecked")
                    RowMapper<VectorSearchResult> rowMapper = invocation.getArgument(2);
                    assertThat(params.getValue("objectType")).isNull();
                    assertThat(params.getValue("objectId")).isNull();

                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("object_id")).thenReturn("object-1");
                    when(rs.getString("text")).thenReturn("stored chunk");
                    when(rs.getString("metadata")).thenReturn("{\"documentId\":\"doc-99\",\"topic\":\"greeting\"}");
                    when(rs.getDouble("distance")).thenReturn(0.25d);
                    return List.of(rowMapper.mapRow(rs, 0));
                });

        List<VectorSearchResult> results = adapter.searchByObject(
                " ",
                "",
                new VectorSearchRequest(List.of(0.4d, 0.5d), 2));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-99");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "greeting");
        assertThat(results.get(0).score()).isGreaterThan(0.0d);
        verify(namedParameterJdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void hybridSearchPassesWeightsToSqlSetBackedQuery() {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        adapter.hybridSearch("hello", new VectorSearchRequest(List.of(0.7d, 0.8d), 5), 0.6d, 0.4d);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedParameterJdbcTemplate).query(org.mockito.Mockito.eq(HYBRID_SEARCH_SQL), paramsCaptor.capture(), any(RowMapper.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("query")).isEqualTo("hello");
        assertThat(params.getValue("vectorWeight")).isEqualTo(0.6d);
        assertThat(params.getValue("lexicalWeight")).isEqualTo(0.4d);
        assertThat(params.getValue("limit")).isEqualTo(5);
        assertThat(params.getValue("vector")).isInstanceOf(PGvector.class);
    }

    @Test
    void listByObjectUsesConfiguredLimitAndMapsMetadataDocumentId() throws SQLException {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource params = invocation.getArgument(1);
                    @SuppressWarnings("unchecked")
                    RowMapper<VectorSearchResult> rowMapper = invocation.getArgument(2);
                    assertThat(params.getValue("objectType")).isEqualTo("ARTICLE");
                    assertThat(params.getValue("objectId")).isEqualTo("article-1");
                    assertThat(params.getValue("limit")).isEqualTo(2);

                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("object_id")).thenReturn("article-1");
                    when(rs.getString("text")).thenReturn("chunk body");
                    when(rs.getString("metadata")).thenReturn("{\"documentId\":\"doc-list\",\"topic\":\"beta\"}");
                    return List.of(rowMapper.mapRow(rs, 0));
                });

        List<VectorSearchResult> results = adapter.listByObject("ARTICLE", "article-1", 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-list");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "beta");
        assertThat(results.get(0).score()).isEqualTo(1.0d);
        verify(namedParameterJdbcTemplate).query(org.mockito.Mockito.eq(LIST_BY_OBJECT_SQL), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void getMetadataReturnsFirstRowAsImmutableMap() {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(Map.of("documentId", "doc-meta", "topic", "gamma")));

        Map<String, Object> metadata = adapter.getMetadata("ARTICLE", "article-1");

        assertThat(metadata).containsEntry("documentId", "doc-meta");
        assertThat(metadata).containsEntry("topic", "gamma");
        verify(namedParameterJdbcTemplate).query(org.mockito.Mockito.eq(METADATA_BY_OBJECT_SQL), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = PgVectorStoreAdapterV2.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(adapter, value);
    }
}
