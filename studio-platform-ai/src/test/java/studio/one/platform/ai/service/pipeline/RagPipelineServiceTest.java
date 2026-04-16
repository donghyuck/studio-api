package studio.one.platform.ai.service.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.keyword.KeywordExtractor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagPipelineServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private TextChunker textChunker;

    @Mock
    private KeywordExtractor keywordExtractor;

    private Cache<String, List<Double>> cache;
    private Retry retry;

    private RagPipelineService ragPipelineService;

    @Captor
    private ArgumentCaptor<List<VectorDocument>> documentsCaptor;

    @Captor
    private ArgumentCaptor<Integer> limitCaptor;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build());
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry, keywordExtractor);
    }

    @Test
    void shouldIndexChunksAndPersistVectors() {
        RagIndexRequest request = new RagIndexRequest("doc-1", "hello world", Map.of("author", "test"));
        when(textChunker.chunk("doc-1", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-1-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-1-0", List.of(0.1, 0.2, 0.3)))));

        ragPipelineService.index(request);

        verify(vectorStorePort).upsert(documentsCaptor.capture());
        List<VectorDocument> documents = documentsCaptor.getValue();
        assertThat(documents).hasSize(1);
        VectorDocument document = documents.get(0);
        assertThat(document.id()).isEqualTo("doc-1-0");
        assertThat(document.metadata()).containsEntry("author", "test");
        assertThat(document.metadata()).containsEntry("chunkOrder", 0);
        assertThat(document.embedding()).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    void shouldExtractKeywordsWithLlmWhenEnabled() {
        RagIndexRequest request = new RagIndexRequest("doc-kw", "hello world", Map.of(), List.of(), true);
        when(textChunker.chunk("doc-kw", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-kw-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-kw-0", List.of(0.1, 0.2, 0.3)))));
        when(keywordExtractor.extract("hello world")).thenReturn(List.of("hello", "world"));

        ragPipelineService.index(request);

        verify(keywordExtractor).extract("hello world");
        verify(vectorStorePort).upsert(documentsCaptor.capture());
        VectorDocument document = documentsCaptor.getValue().get(0);
        assertThat(document.metadata()).containsEntry("keywords", List.of("hello", "world"));
    }

    @Test
    void shouldSearchAndMapResults() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(new VectorDocument("doc-1", "chunk", Map.of("author", "test"), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        RagSearchResult result = results.get(0);
        assertThat(result.documentId()).isEqualTo("doc-1");
        assertThat(result.metadata()).containsEntry("author", "test");
        assertThat(result.score()).isEqualTo(0.9);
    }

    @Test
    void shouldFallbackToKeywordEnrichedHybridSearchWhenInitialResultsAreLowRelevance() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of("greeting", "salutation"));
        when(vectorStorePort.hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.05)));
        when(vectorStorePort.hybridSearch(eq("hello greeting salutation"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-strong", "better", Map.of("source", "keyword-fallback"), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("doc-strong");
        assertThat(results.get(0).metadata()).containsEntry("source", "keyword-fallback");
        verify(vectorStorePort).hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
        verify(vectorStorePort).hybridSearch(eq("hello greeting salutation"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldFallbackToSemanticSearchWhenKeywordExtractionFailsAndHybridReturnsNoHits() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(keywordExtractor.extract("hello")).thenThrow(new RuntimeException("keyword failure"));
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic", "semantic hit", Map.of("source", "semantic-fallback"), List.of(0.5, 0.6)), 0.7)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("doc-semantic");
        assertThat(results.get(0).metadata()).containsEntry("source", "semantic-fallback");
        verify(vectorStorePort, times(1)).hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
        verify(vectorStorePort).search(any(VectorSearchRequest.class));
    }

    @Test
    void shouldReturnEmptyWhenNoRelevantHitsRemainAfterFallbacks() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of());
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low-semantic", "weak semantic", Map.of(), List.of(0.5, 0.6)), 0.02)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldFallbackToKeywordEnrichedHybridSearchWithinObjectScope() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of("greeting", "salutation"));
        when(vectorStorePort.hybridSearchByObject(eq("hello"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.05)));
        when(vectorStorePort.hybridSearchByObject(eq("hello greeting salutation"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-strong", "better", Map.of("source", "object-keyword-fallback"), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.searchByObject(request, "attachment", "42");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("doc-strong");
        assertThat(results.get(0).metadata()).containsEntry("source", "object-keyword-fallback");
        verify(vectorStorePort).hybridSearchByObject(eq("hello"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
        verify(vectorStorePort).hybridSearchByObject(eq("hello greeting salutation"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldFallbackToSemanticSearchWithinObjectScopeWhenKeywordExtractionFails() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearchByObject(anyString(), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(keywordExtractor.extract("hello")).thenThrow(new RuntimeException("keyword failure"));
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic", "semantic hit", Map.of("source", "object-semantic-fallback"), List.of(0.5, 0.6)), 0.7)));

        List<RagSearchResult> results = ragPipelineService.searchByObject(request, "attachment", "42");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("doc-semantic");
        assertThat(results.get(0).metadata()).containsEntry("source", "object-semantic-fallback");
        verify(vectorStorePort, times(1)).hybridSearchByObject(eq("hello"), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
        verify(vectorStorePort).searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class));
    }

    @Test
    void shouldReturnEmptyWhenObjectScopedFallbacksRemainLowRelevance() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of());
        when(vectorStorePort.hybridSearchByObject(anyString(), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low-semantic", "weak semantic", Map.of(), List.of(0.5, 0.6)), 0.02)));

        List<RagSearchResult> results = ragPipelineService.searchByObject(request, "attachment", "42");

        assertThat(results).isEmpty();
    }

    @Test
    void shouldPassConfiguredWeightsToHybridSearch() {
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, new RagPipelineOptions(0.2d, 0.8d, 0.15d, true, true, 20, 100));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), eq(0.2d), eq(0.8d)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-weighted", "weighted", Map.of(), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        verify(vectorStorePort).hybridSearch(eq("hello"), any(VectorSearchRequest.class), eq(0.2d), eq(0.8d));
    }

    @Test
    void shouldRejectNonPositiveCombinedHybridWeights() {
        assertThatThrownBy(() -> new RagPipelineOptions(0.0d, 0.0d, 0.15d, true, true, 20, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive sum");
    }

    @Test
    void shouldUseConfiguredMinimumRelevanceScore() {
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, new RagPipelineOptions(0.7d, 0.3d, 0.5d, false, false, 20, 100));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.49)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).isEmpty();
        verify(keywordExtractor, never()).extract(anyString());
        verify(vectorStorePort, never()).search(any(VectorSearchRequest.class));
    }

    @Test
    void shouldSkipKeywordFallbackWhenDisabled() {
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, new RagPipelineOptions(0.7d, 0.3d, 0.15d, false, true, 20, 100));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic", "semantic", Map.of(), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).documentId()).isEqualTo("doc-semantic");
        verify(keywordExtractor, never()).extract(anyString());
        verify(vectorStorePort, times(1)).hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldSkipSemanticFallbackWhenDisabled() {
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, new RagPipelineOptions(0.7d, 0.3d, 0.15d, true, false, 20, 100));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of());
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).isEmpty();
        verify(vectorStorePort, never()).search(any(VectorSearchRequest.class));
    }

    @Test
    void shouldClampObjectListLimitInServiceLayer() {
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, new RagPipelineOptions(0.7d, 0.3d, 0.15d, true, true, 5, 10));
        when(vectorStorePort.listByObject(eq("attachment"), eq("42"), any()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc", "content", Map.of(), List.of()), 1.0d)));

        ragPipelineService.listByObject("attachment", "42", null);
        ragPipelineService.listByObject("attachment", "42", 0);
        ragPipelineService.listByObject("attachment", "42", 30);
        ragPipelineService.listByObject("attachment", "42", 7);

        verify(vectorStorePort, times(4)).listByObject(eq("attachment"), eq("42"), limitCaptor.capture());
        assertThat(limitCaptor.getAllValues()).containsExactly(5, 5, 10, 7);
    }
}
