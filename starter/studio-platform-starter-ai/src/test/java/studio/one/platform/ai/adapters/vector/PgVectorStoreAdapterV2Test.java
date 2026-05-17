package studio.one.platform.ai.adapters.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pgvector.PGvector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorChunkParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorHybridSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorMapper;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchParameter;
import studio.one.platform.ai.adapters.vector.mybatis.PgVectorSearchRow;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;

class PgVectorStoreAdapterV2Test {

    private PgVectorMapper mapper;
    private PgVectorStoreAdapterV2 adapter;

    @BeforeEach
    void setUp() {
        mapper = mock(PgVectorMapper.class);
        adapter = new PgVectorStoreAdapterV2(mapper);
    }

    @Test
    void upsertBindsMapperParametersForActiveAdapterPath() {
        VectorDocument document = new VectorDocument(
                "doc-1",
                "hello world",
                Map.of("objectType", "ARTICLE", "objectId", "article-1", "chunkOrder", 3, "topic", "greeting"),
                List.of(0.1d, 0.2d, 0.3d));

        adapter.upsert(List.of(document));

        ArgumentCaptor<PgVectorChunkParameter> captor = ArgumentCaptor.forClass(PgVectorChunkParameter.class);
        verify(mapper).upsertChunk(captor.capture());
        PgVectorChunkParameter params = captor.getValue();
        assertThat(params.getObjectType()).isEqualTo("ARTICLE");
        assertThat(params.getObjectId()).isEqualTo("article-1");
        assertThat(params.getChunkIndex()).isEqualTo(3);
        assertThat(params.getText()).isEqualTo("hello world");
        assertThat(params.getMetadata()).contains("\"documentId\":\"doc-1\"");
        assertThat(params.getEmbedding()).isInstanceOf(PGvector.class);
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

        ArgumentCaptor<PgVectorChunkParameter> captor = ArgumentCaptor.forClass(PgVectorChunkParameter.class);
        verify(mapper).upsertChunk(captor.capture());
        PgVectorChunkParameter params = captor.getValue();
        assertThat(params.getObjectType()).isEqualTo("ARTICLE");
        assertThat(params.getObjectId()).isEqualTo("article-1");
        assertThat(params.getChunkIndex()).isEqualTo(7);
        assertThat(params.getMetadata()).contains("\"chunkOrder\":7");
    }

    @Test
    void searchUsesMapperAndMapsMetadataDocumentId() {
        when(mapper.search(any(PgVectorSearchParameter.class))).thenReturn(List.of(row(
                42L,
                "object-1",
                "stored chunk",
                "{\"documentId\":\"doc-42\",\"topic\":\"alpha\"}",
                0.5d)));

        List<VectorSearchResult> results = adapter.search(new VectorSearchRequest(List.of(0.2d, 0.3d), 3));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-42");
        assertThat(results.get(0).document().metadata())
                .containsEntry("topic", "alpha")
                .containsEntry("_vectorRowId", "row-42");
        ArgumentCaptor<PgVectorSearchParameter> captor = ArgumentCaptor.forClass(PgVectorSearchParameter.class);
        verify(mapper).search(captor.capture());
        assertThat(captor.getValue().getLimit()).isEqualTo(3);
        assertThat(captor.getValue().getVector()).isInstanceOf(PGvector.class);
    }

    @Test
    void searchAppliesMetadataEqualsAndInCriteriaToMapperParameter() {
        when(mapper.search(any(PgVectorSearchParameter.class))).thenReturn(List.of());
        MetadataFilter filter = MetadataFilter.of(
                Map.of(
                        VectorRecord.KEY_OBJECT_TYPE, "attachment",
                        VectorRecord.KEY_OBJECT_ID, "42",
                        VectorRecord.KEY_EMBEDDING_PROFILE_ID, "retrieval",
                        VectorRecord.KEY_EMBEDDING_MODEL, "gemini-embedding-001"),
                Map.of(VectorRecord.KEY_EMBEDDING_INPUT_TYPE, List.of("TEXT", "TABLE_TEXT")),
                Map.of());

        adapter.search(new VectorSearchRequest(List.of(0.2d, 0.3d), 3, filter));

        ArgumentCaptor<PgVectorSearchParameter> captor = ArgumentCaptor.forClass(PgVectorSearchParameter.class);
        verify(mapper).search(captor.capture());
        PgVectorSearchParameter params = captor.getValue();
        assertThat(params.getMetadataObjectType()).isEqualTo("attachment");
        assertThat(params.getMetadataObjectId()).isEqualTo("42");
        assertThat(params.getEqualsCriteria())
                .extracting("key")
                .containsExactlyInAnyOrder(VectorRecord.KEY_EMBEDDING_PROFILE_ID, VectorRecord.KEY_EMBEDDING_MODEL);
        assertThat(params.getInCriteria()).singleElement()
                .satisfies(criterion -> {
                    assertThat(criterion.getKey()).isEqualTo(VectorRecord.KEY_EMBEDDING_INPUT_TYPE);
                    assertThat(criterion.getValues()).containsExactly("TEXT", "TABLE_TEXT");
                });
    }

    @Test
    void deleteByObjectUsesMapper() {
        adapter.deleteByObject("ARTICLE", "article-1");

        verify(mapper).deleteByObject("ARTICLE", "article-1");
    }

    @Test
    void replaceByObjectDeletesThenUpsertsWhenNoTransactionManagerIsAvailable() {
        VectorDocument document = new VectorDocument(
                "doc-replace",
                "replacement",
                Map.of("objectType", "ARTICLE", "objectId", "article-1", "chunkOrder", 0),
                List.of(0.1d, 0.2d));

        adapter.replaceByObject("ARTICLE", "article-1", List.of(document));

        verify(mapper).deleteByObject("ARTICLE", "article-1");
        verify(mapper).upsertChunk(any(PgVectorChunkParameter.class));
    }

    @Test
    void searchByObjectNormalizesBlankFiltersAndMapsMetadataDocumentId() {
        when(mapper.searchByObject(any(PgVectorSearchParameter.class))).thenReturn(List.of(row(
                null,
                "object-1",
                "stored chunk",
                "{\"documentId\":\"doc-99\",\"topic\":\"greeting\"}",
                0.25d)));

        List<VectorSearchResult> results = adapter.searchByObject(
                " ",
                "",
                new VectorSearchRequest(List.of(0.4d, 0.5d), 2));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-99");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "greeting");
        assertThat(results.get(0).score()).isGreaterThan(0.0d);
        ArgumentCaptor<PgVectorSearchParameter> captor = ArgumentCaptor.forClass(PgVectorSearchParameter.class);
        verify(mapper).searchByObject(captor.capture());
        assertThat(captor.getValue().getObjectType()).isNull();
        assertThat(captor.getValue().getObjectId()).isNull();
    }

    @Test
    void hybridSearchPassesWeightsToMapper() {
        when(mapper.hybridSearch(any(PgVectorHybridSearchParameter.class))).thenReturn(List.of());

        adapter.hybridSearch("hello", new VectorSearchRequest(List.of(0.7d, 0.8d), 5), 0.6d, 0.4d);

        ArgumentCaptor<PgVectorHybridSearchParameter> captor =
                ArgumentCaptor.forClass(PgVectorHybridSearchParameter.class);
        verify(mapper).hybridSearch(captor.capture());
        PgVectorHybridSearchParameter params = captor.getValue();
        assertThat(params.getQuery()).isEqualTo("hello");
        assertThat(params.getVectorWeight()).isEqualTo(0.6d);
        assertThat(params.getLexicalWeight()).isEqualTo(0.4d);
        assertThat(params.getLimit()).isEqualTo(5);
        assertThat(params.getVector()).isInstanceOf(PGvector.class);
    }

    @Test
    void listByObjectUsesConfiguredLimitAndMapsMetadataDocumentId() {
        when(mapper.listByObject(eq("ARTICLE"), eq("article-1"), eq(2))).thenReturn(List.of(row(
                null,
                "article-1",
                "chunk body",
                "{\"documentId\":\"doc-list\",\"topic\":\"beta\"}",
                null)));

        List<VectorSearchResult> results = adapter.listByObject("ARTICLE", "article-1", 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-list");
        assertThat(results.get(0).document().metadata()).containsEntry("topic", "beta");
        assertThat(results.get(0).score()).isEqualTo(1.0d);
        verify(mapper).listByObject("ARTICLE", "article-1", 2);
    }

    @Test
    void listByObjectWithFiltersDelegatesQueryAndPageToMapper() {
        when(mapper.listByObjectPageFiltered(
                eq("ARTICLE"), eq("article-1"), eq("doc-list"), eq("Spring"), eq(50), eq(51)))
                .thenReturn(List.of(row(
                        null,
                        "article-1",
                        "Spring content",
                        "{\"documentId\":\"doc-list\",\"chunkId\":\"chunk-1\"}",
                        null)));

        List<VectorSearchResult> results = adapter.listByObject(
                "ARTICLE", "article-1", "doc-list", "Spring", 50, 51);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).document().id()).isEqualTo("doc-list");
        verify(mapper).listByObjectPageFiltered("ARTICLE", "article-1", "doc-list", "Spring", 50, 51);
    }

    @Test
    void getMetadataReturnsFirstRowAsImmutableMap() {
        when(mapper.metadataByObject("ARTICLE", "article-1"))
                .thenReturn("{\"documentId\":\"doc-meta\",\"topic\":\"gamma\"}");

        Map<String, Object> metadata = adapter.getMetadata("ARTICLE", "article-1");

        assertThat(metadata).containsEntry("documentId", "doc-meta");
        assertThat(metadata).containsEntry("topic", "gamma");
        verify(mapper).metadataByObject("ARTICLE", "article-1");
    }

    @Test
    void existsReturnsMapperCountResult() {
        when(mapper.exists("ARTICLE", "article-1")).thenReturn(1);

        assertThat(adapter.exists("ARTICLE", "article-1")).isTrue();
    }

    private static PgVectorSearchRow row(Long id, String objectId, String text, String metadata, Double distance) {
        PgVectorSearchRow row = new PgVectorSearchRow();
        row.setId(id);
        row.setObjectId(objectId);
        row.setText(text);
        row.setMetadata(metadata);
        row.setDistance(distance);
        return row;
    }
}
