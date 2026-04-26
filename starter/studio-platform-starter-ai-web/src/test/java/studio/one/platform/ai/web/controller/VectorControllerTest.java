package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.web.dto.VectorSearchRequestDto;
import studio.one.platform.ai.web.dto.VectorSearchResultDto;
import studio.one.platform.web.dto.ApiResponse;

class VectorControllerTest {

    private EmbeddingPort embeddingPort;
    private VectorStorePort vectorStorePort;
    private VectorController controller;

    @BeforeEach
    void setUp() {
        embeddingPort = mock(EmbeddingPort.class);
        vectorStorePort = mock(VectorStorePort.class);
        controller = new VectorController(embeddingPort, vectorStorePort);
    }

    @Test
    void searchesWithinObjectScopeWhenObjectFilterIsProvided() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-1", "chunk", Map.of("objectType", "attachment", "objectId", "42"), List.of()),
                        0.8d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, "attachment", "42", null));

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).id()).isEqualTo("doc-1");
        assertThat(response.getBody().getData().get(0).documentId()).isEqualTo("doc-1");
        verify(vectorStorePort).searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class));
    }

    @Test
    void mapsVectorSearchHitProvenanceToLegacyResponseShape() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("chunk-1", "chunk", Map.of(
                                VectorRecord.KEY_DOCUMENT_ID, "doc-1",
                                VectorRecord.KEY_CHUNK_ID, "chunk-1",
                                VectorRecord.KEY_SOURCE_REF, "page[1]/p[0]"), List.of()),
                        0.8d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, "attachment", "42", null));

        VectorSearchResultDto result = response.getBody().getData().get(0);
        assertThat(result.id()).isEqualTo("chunk-1");
        assertThat(result.documentId()).isEqualTo("doc-1");
        assertThat(result.content()).isEqualTo("chunk");
        assertThat(result.metadata()).containsEntry(VectorRecord.KEY_SOURCE_REF, "page[1]/p[0]");
    }

    @Test
    void usesAggregateVectorSearchForGlobalSearch() {
        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class)))
                .thenReturn(VectorSearchResults.of(List.of(new VectorSearchHit(
                        "chunk-1",
                        "doc-1",
                        "chunk-1",
                        null,
                        "chunk",
                        0.8d,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("topic", "alpha"))), 2L));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, null, null, null));

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).documentId()).isEqualTo("doc-1");
        assertThat(response.getBody().getData().get(0).metadata()).containsEntry("topic", "alpha");
        verify(vectorStorePort).searchWithFilter(captor.capture());
        assertThat(captor.getValue().queryText()).isEqualTo("hello");
        assertThat(captor.getValue().includeText()).isTrue();
        assertThat(captor.getValue().includeMetadata()).isTrue();
    }

    @Test
    void passesObjectFilterAndMinScoreThroughCoreSearchRequest() {
        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-1", "chunk", Map.of(), List.of()),
                        0.8d)));

        controller.search(new VectorSearchRequestDto("hello", null, 3, false, "attachment", "42", 0.4d));

        verify(vectorStorePort).searchByObject(eq("attachment"), eq("42"), captor.capture());
        assertThat(captor.getValue().metadataFilter().objectType()).isEqualTo("attachment");
        assertThat(captor.getValue().metadataFilter().objectId()).isEqualTo("42");
        assertThat(captor.getValue().minScore()).isEqualTo(0.4d);
    }

    @Test
    void usesHybridSearchWithinObjectScopeWhenRequested() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.hybridSearchByObject(eq("hello"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), eq(0.7d), eq(0.3d)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-2", "hybrid", Map.of("objectType", "attachment", "objectId", "42"), List.of()),
                        0.9d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, true, "attachment", "42", null));

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).documentId()).isEqualTo("doc-2");
        verify(vectorStorePort).hybridSearchByObject(eq("hello"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), eq(0.7d), eq(0.3d));
    }

    @Test
    void filtersResultsByMinScore() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class)))
                .thenReturn(VectorSearchResults.of(List.of(
                        new VectorSearchHit("doc-low", "doc-low", "doc-low", null, "low", 0.39d,
                                null, null, null, null, null, Map.of()),
                        new VectorSearchHit("doc-high", "doc-high", "doc-high", null, "high", 0.61d,
                                null, null, null, null, null, Map.of())), 1L));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, null, null, 0.4d));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-high");
    }

    @Test
    void passesIncludeTextAndIncludeMetadataThroughCoreSearchRequest() {
        ArgumentCaptor<VectorSearchRequest> captor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class)))
                .thenReturn(VectorSearchResults.of(List.of(new VectorSearchHit(
                        "chunk-1",
                        "doc-1",
                        "chunk-1",
                        null,
                        null,
                        0.8d,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of())), 1L));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, null, null, null, false, false));

        verify(vectorStorePort).searchWithFilter(captor.capture());
        assertThat(captor.getValue().includeText()).isFalse();
        assertThat(captor.getValue().includeMetadata()).isFalse();
        assertThat(response.getBody().getData().get(0).content()).isNull();
        assertThat(response.getBody().getData().get(0).metadata()).isEmpty();
    }

    @Test
    void searchesWithinObjectScopeWhenOnlyObjectTypeIsProvided() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchByObject(eq("attachment"), eq(null), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-type-only", "chunk", Map.of("objectType", "attachment"), List.of()),
                        0.75d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, "attachment", null, null));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-type-only");
        verify(vectorStorePort).searchByObject(eq("attachment"), eq(null), any(VectorSearchRequest.class));
    }

    @Test
    void searchesWithinObjectScopeWhenOnlyObjectIdIsProvided() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchByObject(eq(null), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-id-only", "chunk", Map.of("objectId", "42"), List.of()),
                        0.76d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, null, "42", null));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-id-only");
        verify(vectorStorePort).searchByObject(eq(null), eq("42"), any(VectorSearchRequest.class));
    }

    @Test
    void treatsBlankObjectFiltersAsAbsent() {
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("query", List.of(1.0, 0.0)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class)))
                .thenReturn(VectorSearchResults.of(List.of(new VectorSearchHit(
                        "doc-global",
                        "doc-global",
                        "doc-global",
                        null,
                        "chunk",
                        0.7d,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of())), 1L));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, "   ", "", null));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-global");
        verify(vectorStorePort).searchWithFilter(any(VectorSearchRequest.class));
    }

    @Test
    void rejectsHybridObjectSearchWithoutQueryText() {
        assertThrows(ResponseStatusException.class, () -> controller.search(
                new VectorSearchRequestDto(null, List.of(1.0, 0.0), 3, true, "attachment", "42", null)));
    }
}
