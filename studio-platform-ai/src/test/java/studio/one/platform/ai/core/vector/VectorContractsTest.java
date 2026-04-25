package studio.one.platform.ai.core.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.MetadataRange;

class VectorContractsTest {

    @Test
    void vectorDocumentDefensivelyCopiesMetadataAndEmbedding() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("objectType", "attachment");
        List<Double> embedding = new ArrayList<>(List.of(0.1d, 0.2d));

        VectorDocument document = new VectorDocument("doc-1", "content", metadata, embedding);
        metadata.put("objectId", "42");
        embedding.add(0.3d);

        assertThat(document.metadata()).containsExactly(Map.entry("objectType", "attachment"));
        assertThat(document.embedding()).containsExactly(0.1d, 0.2d);
        assertThatThrownBy(() -> document.metadata().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> document.embedding().add(0.4d))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void vectorSearchRequestKeepsCompatibilityWhileCarryingMetadataPredicates() {
        MetadataRange<Integer> versionRange = MetadataRange.closed(1, 3);
        List<Object> statuses = new ArrayList<>();
        statuses.add(" READY ");
        statuses.add("");
        statuses.add("READY");
        statuses.add(null);
        MetadataFilter filter = MetadataFilter.of(
                Map.of(" objectType ", " attachment "),
                Map.of(" status ", statuses),
                Map.of(" version ", versionRange));

        VectorSearchRequest request = new VectorSearchRequest(
                List.of(0.1d, 0.2d),
                "semantic query",
                5,
                filter,
                0.4d,
                false,
                false);

        assertThat(legacyEmbedding(request)).containsExactly(0.1d, 0.2d);
        assertThat(request.queryVector()).containsExactly(0.1d, 0.2d);
        assertThat(request.queryText()).isEqualTo("semantic query");
        assertThat(request.topK()).isEqualTo(5);
        assertThat(request.metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(request.metadataFilter().equalsCriteria()).containsEntry("objectType", "attachment");
        assertThat(request.metadataFilter().inCriteria()).containsEntry("status", List.of("READY"));
        assertThat(request.metadataFilter().rangeCriteria()).containsEntry("version", versionRange);
        assertThat(request.minScore()).isEqualTo(0.4d);
        assertThat(request.hasMinScore()).isTrue();
        assertThat(request.includeText()).isFalse();
        assertThat(request.includeMetadata()).isFalse();
    }

    @SuppressWarnings("deprecation")
    private static List<Double> legacyEmbedding(VectorSearchRequest request) {
        return request.embedding();
    }

    @Test
    void metadataRangeFactoriesExposeInclusiveAndExclusiveBounds() {
        MetadataRange<Integer> closed = MetadataRange.closed(1, 3);
        MetadataRange<Integer> open = MetadataRange.open(1, 3);

        assertThat(closed.from()).isEqualTo(1);
        assertThat(closed.to()).isEqualTo(3);
        assertThat(closed.includeFrom()).isTrue();
        assertThat(closed.includeTo()).isTrue();
        assertThat(open.includeFrom()).isFalse();
        assertThat(open.includeTo()).isFalse();
        assertThat(MetadataRange.atLeast(2).to()).isNull();
        assertThat(MetadataRange.atMost(4).from()).isNull();
    }

    @Test
    void metadataRangeRejectsEmptyAndReversedBounds() {
        assertThatThrownBy(() -> new MetadataRange<Integer>(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from or to");
        assertThatThrownBy(() -> MetadataRange.closed(3, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from must be less than or equal to to");
    }

    @Test
    void vectorStoreDefaultSearchByObjectFiltersByScopeAndTopK() {
        TestVectorStore store = new TestVectorStore(List.of(
                result("doc-1", "ATTACHMENT", "42"),
                result("doc-2", "attachment", "42"),
                result("doc-3", "attachment", "99")));

        List<VectorSearchResult> results = store.searchByObject(
                "attachment",
                "42",
                new VectorSearchRequest(List.of(0.1d), 1));

        assertThat(results)
                .extracting(result -> result.document().id())
                .containsExactly("doc-1");
    }

    @Test
    void vectorStoreDefaultAdaptersDelegateToSearchPaths() {
        TestVectorStore store = new TestVectorStore(List.of(result("doc-1", "attachment", "42")));
        VectorSearchRequest request = new VectorSearchRequest(List.of(0.1d), 3);

        assertThat(store.hybridSearch("hello", request, 0.7d, 0.3d)).hasSize(1);
        assertThat(store.hybridSearchByObject("hello", "attachment", "42", request, 0.7d, 0.3d))
                .hasSize(1);
        assertThat(store.searchRequests()).containsExactly(request, request);
    }

    @Test
    void vectorStoreUnsupportedDefaultsRemainExplicit() {
        UnsupportedDefaultVectorStore store = new UnsupportedDefaultVectorStore();

        assertThatThrownBy(() -> store.deleteByObject("attachment", "42"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deleteByObject");
        assertThatThrownBy(() -> store.listByObject("attachment", "42", 10))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("listByObject");
        assertThatThrownBy(() -> store.getMetadata("attachment", "42"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("getMetadata");
        assertThatThrownBy(() -> store.deleteByDocumentId("doc-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deleteByDocumentId");
        assertThatThrownBy(() -> store.deleteByChunkId("chunk-1"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("deleteByChunkId");
        assertThat(store.existsByContentHash("hash")).isFalse();
    }

    @Test
    void vectorStoreReplaceByObjectDeletesThenUpsertsByDefault() {
        TestVectorStore store = new TestVectorStore(List.of());
        List<VectorDocument> documents = List.of(new VectorDocument("doc-1", "content", Map.of(), List.of(0.1d)));

        store.replaceByObject("attachment", "42", documents);

        assertThat(store.operations()).containsExactly("delete:attachment:42", "upsert:1");
    }

    @Test
    void vectorRecordPreservesChunkFieldsInVectorDocumentMetadata() {
        VectorRecord record = record(Map.of(
                VectorRecord.KEY_DOCUMENT_ID, "caller-doc",
                VectorRecord.KEY_CHUNK_INDEX, 7,
                VectorRecord.KEY_TENANT_ID, "tenant-a"));

        VectorDocument document = record.toVectorDocument();

        assertThat(document.id()).isEqualTo("record-1");
        assertThat(document.content()).isEqualTo("chunk text");
        assertThat(document.embedding()).containsExactly(0.1d, 0.2d);
        assertThat(document.metadata())
                .containsEntry(VectorRecord.KEY_DOCUMENT_ID, "doc-1")
                .containsEntry(VectorRecord.KEY_CHUNK_ID, "chunk-1")
                .containsEntry(VectorRecord.KEY_PARENT_CHUNK_ID, "parent-1")
                .containsEntry(VectorRecord.KEY_CONTENT_HASH, "hash-1")
                .containsEntry(VectorRecord.KEY_EMBEDDING_MODEL, "embedding-model")
                .containsEntry(VectorRecord.KEY_EMBEDDING_DIMENSION, 2)
                .containsEntry(VectorRecord.KEY_CHUNK_INDEX, 7)
                .containsEntry(VectorRecord.KEY_TENANT_ID, "tenant-a");
    }

    @Test
    void vectorRecordBuilderCreatesRecordWithoutLongConstructorOrderingRisk() {
        VectorRecord record = VectorRecord.builder()
                .id("record-1")
                .documentId("doc-1")
                .chunkId("chunk-1")
                .parentChunkId("parent-1")
                .contentHash("hash-1")
                .text("chunk text")
                .embedding(List.of(0.1d, 0.2d))
                .embeddingModel("embedding-model")
                .chunkType("child")
                .headingPath("Intro")
                .sourceRef("page[1]/p[0]")
                .page(1)
                .metadata(Map.of(VectorRecord.KEY_CHUNK_INDEX, 7))
                .build();

        assertThat(record.embeddingDimension()).isEqualTo(2);
        assertThat(record.toMetadata())
                .containsEntry(VectorRecord.KEY_DOCUMENT_ID, "doc-1")
                .containsEntry(VectorRecord.KEY_CHUNK_ID, "chunk-1")
                .containsEntry(VectorRecord.KEY_CHUNK_INDEX, 7);
    }

    @Test
    void vectorRecordRejectsInvalidRequiredFields() {
        assertThatThrownBy(() -> record("", List.of(0.1d), 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> new VectorRecord(
                "record-1",
                "doc-1",
                "chunk-1",
                null,
                "hash-1",
                "text",
                List.of(0.1d),
                "model",
                2,
                null,
                null,
                null,
                null,
                null,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension");
        assertThatThrownBy(() -> record("record-1", List.of(), 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding must not be empty");
    }

    @Test
    void vectorStoreDefaultRecordAdaptersAndAggregateSearchUseLegacyContract() {
        TestVectorStore store = new TestVectorStore(List.of(new VectorSearchResult(record(Map.of()).toVectorDocument(), 0.8d)));

        store.upsert(record(Map.of()));
        store.upsertAll(List.of());
        VectorSearchResults results = store.searchWithFilter(new VectorSearchRequest(List.of(0.1d), 3));

        assertThat(store.operations()).containsExactly("upsert:1");
        assertThat(store.upsertedDocuments())
                .extracting(VectorDocument::id)
                .containsExactly("record-1");
        assertThat(results.elapsedMs()).isGreaterThanOrEqualTo(0L);
        assertThat(results.hits()).hasSize(1);
        assertThat(results.hits().get(0).documentId()).isEqualTo("doc-1");
        assertThat(results.hits().get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(results.hits().get(0).parentChunkId()).isEqualTo("parent-1");
        assertThat(results.hits().get(0).score()).isEqualTo(0.8d);
    }

    @Test
    void vectorStoreDefaultAggregateSearchHonorsIncludeFlags() {
        TestVectorStore store = new TestVectorStore(List.of(new VectorSearchResult(record(Map.of()).toVectorDocument(), 0.8d)));
        VectorSearchRequest request = new VectorSearchRequest(
                List.of(0.1d),
                null,
                3,
                MetadataFilter.empty(),
                null,
                false,
                false);

        VectorSearchResults results = store.searchRecords(request);

        assertThat(results.hits()).hasSize(1);
        assertThat(results.hits().get(0).text()).isNull();
        assertThat(results.hits().get(0).metadata()).isEmpty();
        assertThat(results.hits().get(0).documentId()).isEqualTo("doc-1");
        assertThat(results.hits().get(0).chunkId()).isEqualTo("chunk-1");
    }

    @Test
    void vectorSearchHitAdaptsLegacySearchResultMetadata() {
        VectorSearchHit hit = VectorSearchHit.from(new VectorSearchResult(
                new VectorDocument("record-1", "chunk text", Map.of(
                        VectorRecord.KEY_DOCUMENT_ID, " doc-1 ",
                        VectorRecord.KEY_CHUNK_ID, " chunk-1 ",
                        VectorRecord.KEY_PARENT_CHUNK_ID, "parent-1",
                        VectorRecord.KEY_CHUNK_TYPE, "paragraph",
                        VectorRecord.KEY_HEADING_PATH, "Intro > Body",
                        VectorRecord.KEY_SOURCE_REF, "file.pdf",
                        VectorRecord.KEY_PAGE, 3L,
                        VectorRecord.KEY_SLIDE, 2), List.of()),
                0.93d));

        assertThat(hit.id()).isEqualTo("record-1");
        assertThat(hit.documentId()).isEqualTo("doc-1");
        assertThat(hit.chunkId()).isEqualTo("chunk-1");
        assertThat(hit.parentChunkId()).isEqualTo("parent-1");
        assertThat(hit.text()).isEqualTo("chunk text");
        assertThat(hit.score()).isEqualTo(0.93d);
        assertThat(hit.chunkType()).isEqualTo("paragraph");
        assertThat(hit.headingPath()).isEqualTo("Intro > Body");
        assertThat(hit.sourceRef()).isEqualTo("file.pdf");
        assertThat(hit.page()).isEqualTo(3);
        assertThat(hit.slide()).isEqualTo(2);
        assertThat(hit.metadata()).containsEntry(VectorRecord.KEY_DOCUMENT_ID, " doc-1 ");
    }

    @Test
    void vectorSearchResultsDefensivelyCopiesHitsAndRejectsNegativeElapsed() {
        List<VectorSearchHit> hits = new ArrayList<>();
        hits.add(VectorSearchHit.from(new VectorSearchResult(record(Map.of()).toVectorDocument(), 0.8d)));

        VectorSearchResults results = VectorSearchResults.of(hits, 12L);
        hits.clear();

        assertThat(results.hits()).hasSize(1);
        assertThat(results.elapsedMs()).isEqualTo(12L);
        assertThatThrownBy(() -> results.hits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new VectorSearchResults(List.of(), -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("elapsedMs");
    }

    private static VectorSearchResult result(String id, String objectType, String objectId) {
        return new VectorSearchResult(
                new VectorDocument(id, "content", Map.of(
                        "objectType", objectType,
                        "objectId", objectId), List.of()),
                0.9d);
    }

    private static VectorRecord record(Map<String, Object> metadata) {
        return record("record-1", List.of(0.1d, 0.2d), 2, metadata);
    }

    private static VectorRecord record(
            String id,
            List<Double> embedding,
            int embeddingDimension,
            Map<String, Object> metadata) {
        return new VectorRecord(
                id,
                "doc-1",
                "chunk-1",
                "parent-1",
                "hash-1",
                "chunk text",
                embedding,
                "embedding-model",
                embeddingDimension,
                "child",
                "Intro",
                "page[1]/p[0]",
                1,
                null,
                metadata);
    }

    private static final class TestVectorStore implements VectorStorePort {

        private final List<VectorSearchResult> results;
        private final List<VectorSearchRequest> searchRequests = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();
        private final List<VectorDocument> upsertedDocuments = new ArrayList<>();

        private TestVectorStore(List<VectorSearchResult> results) {
            this.results = results;
        }

        @Override
        public void upsert(List<VectorDocument> documents) {
            operations.add("upsert:" + documents.size());
            upsertedDocuments.addAll(documents);
        }

        @Override
        public void deleteByObject(String objectType, String objectId) {
            operations.add("delete:" + objectType + ":" + objectId);
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            searchRequests.add(request);
            return results;
        }

        @Override
        public boolean exists(String objectType, String objectId) {
            return false;
        }

        private List<VectorSearchRequest> searchRequests() {
            return searchRequests;
        }

        private List<String> operations() {
            return operations;
        }

        private List<VectorDocument> upsertedDocuments() {
            return upsertedDocuments;
        }
    }

    private static final class UnsupportedDefaultVectorStore implements VectorStorePort {

        @Override
        public void upsert(List<VectorDocument> documents) {
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return List.of();
        }

        @Override
        public boolean exists(String objectType, String objectId) {
            return false;
        }
    }
}
