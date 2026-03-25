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
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
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
        assertThat(response.getBody().getData().get(0).documentId()).isEqualTo("doc-1");
        verify(vectorStorePort).searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class));
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
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(
                        new VectorSearchResult(new VectorDocument("doc-low", "low", Map.of(), List.of()), 0.39d),
                        new VectorSearchResult(new VectorDocument("doc-high", "high", Map.of(), List.of()), 0.61d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, null, null, 0.4d));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-high");
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
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-global", "chunk", Map.of(), List.of()),
                        0.7d)));

        ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> response = controller.search(
                new VectorSearchRequestDto("hello", null, 3, false, "   ", "", null));

        assertThat(response.getBody().getData())
                .extracting(VectorSearchResultDto::documentId)
                .containsExactly("doc-global");
        verify(vectorStorePort).search(any(VectorSearchRequest.class));
    }

    @Test
    void rejectsHybridObjectSearchWithoutQueryText() {
        assertThrows(ResponseStatusException.class, () -> controller.search(
                new VectorSearchRequestDto(null, List.of(1.0, 0.0), 3, true, "attachment", "42", null)));
    }
}
