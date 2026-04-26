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
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.chunk.TextChunker;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.cleaning.TextCleaner;
import studio.one.platform.ai.service.cleaning.TextCleaningResult;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.chunking.core.Chunk;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkingContext;
import studio.one.platform.chunking.core.ChunkingOrchestrator;
import studio.one.platform.chunking.core.ChunkingStrategyType;

import java.time.Duration;
import java.util.ArrayList;
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
@SuppressWarnings("deprecation")
class RagPipelineServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private TextChunker textChunker;

    @Mock
    private KeywordExtractor keywordExtractor;

    @Mock
    private TextCleaner textCleaner;

    @Mock
    private ChunkingOrchestrator chunkingOrchestrator;

    private Cache<String, List<Double>> cache;
    private Retry retry;

    private RagPipelineService ragPipelineService;

    @Captor
    private ArgumentCaptor<List<VectorRecord>> recordsCaptor;

    @Captor
    private ArgumentCaptor<Integer> limitCaptor;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build());
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry, keywordExtractor);
    }

    @Test
    void shouldIndexChunksAndPersistVectors() {
        RagIndexRequest request = new RagIndexRequest("doc-1", "hello world", Map.of("author", "test"));
        when(textChunker.chunk("doc-1", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-1-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-1-0", List.of(0.1, 0.2, 0.3)))));

        ragPipelineService.index(request);

        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        List<VectorRecord> records = recordsCaptor.getValue();
        assertThat(records).hasSize(1);
        VectorRecord record = records.get(0);
        assertThat(record.id()).isEqualTo("doc-1-0");
        assertThat(record.documentId()).isEqualTo("doc-1");
        assertThat(record.chunkId()).isEqualTo("doc-1-0");
        assertThat(record.contentHash()).isNotBlank();
        assertThat(record.embeddingModel()).isEqualTo("unknown");
        assertThat(record.embeddingDimension()).isEqualTo(3);
        assertThat(record.metadata()).containsEntry("author", "test");
        assertThat(record.metadata()).containsEntry("cleaned", false);
        assertThat(record.metadata()).containsEntry("cleanerPrompt", "");
        assertThat(record.metadata()).containsEntry("originalTextLength", 11);
        assertThat(record.metadata()).containsEntry("indexedTextLength", 11);
        assertThat(record.metadata()).containsEntry("chunkCount", 1);
        assertThat(record.metadata()).containsEntry("chunkOrder", 0);
        assertThat(record.metadata()).containsEntry("chunkIndex", 0);
        assertThat(record.metadata()).containsEntry("chunkLength", 11);
        assertThat(record.embedding()).containsExactly(0.1, 0.2, 0.3);
    }

    @Test
    void shouldReportIndexProgressWhenListenerIsProvided() {
        RagIndexRequest request = new RagIndexRequest("doc-progress", "hello world", Map.of());
        RecordingProgressListener listener = new RecordingProgressListener();
        when(textChunker.chunk("doc-progress", "hello world"))
                .thenReturn(List.of(
                        new TextChunk("doc-progress-0", "hello"),
                        new TextChunk("doc-progress-1", "world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));

        ragPipelineService.index(request, listener);

        assertThat(listener.steps).containsExactly(
                studio.one.platform.ai.core.rag.RagIndexJobStep.CHUNKING,
                studio.one.platform.ai.core.rag.RagIndexJobStep.EMBEDDING,
                studio.one.platform.ai.core.rag.RagIndexJobStep.INDEXING);
        assertThat(listener.chunkCounts).containsExactly(2);
        assertThat(listener.embeddedCounts).containsExactly(1, 2);
        assertThat(listener.indexedCounts).containsExactly(2);
    }

    @Test
    void shouldPersistVectorRecordMetadataThroughDefaultLegacyAdapter() {
        CapturingVectorStore store = new CapturingVectorStore();
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, store, textChunker, cache, retry,
                keywordExtractor);
        RagIndexRequest request = new RagIndexRequest("doc-adapter", "hello world", Map.of(
                "embeddingModel", "test-embedding"));
        when(textChunker.chunk("doc-adapter", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-adapter-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-adapter-0", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        assertThat(store.upsertedDocuments()).hasSize(1);
        VectorDocument document = store.upsertedDocuments().get(0);
        assertThat(document.id()).isEqualTo("doc-adapter-0");
        assertThat(document.content()).isEqualTo("hello world");
        assertThat(document.embedding()).containsExactly(0.1, 0.2);
        assertThat(document.metadata())
                .containsEntry("cleanerPrompt", "")
                .containsEntry("chunkIndex", 0)
                .containsEntry("chunkOrder", 0)
                .containsEntry("embeddingModel", "test-embedding")
                .containsEntry("embeddingDimension", 2);
    }

    @Test
    void shouldCleanTextBeforeChunkingWhenCleanerIsAvailable() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor, textCleaner, RagPipelineOptions.defaults());
        RagIndexRequest request = new RagIndexRequest("doc-clean", "noisy text", Map.of());
        when(textCleaner.clean("noisy text"))
                .thenReturn(new TextCleaningResult("clean text", true, "rag-cleaner"));
        when(textChunker.chunk("doc-clean", "clean text"))
                .thenReturn(List.of(new TextChunk("doc-clean-0", "clean text")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-clean-0", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        verify(textCleaner).clean("noisy text");
        verify(textChunker).chunk("doc-clean", "clean text");
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        VectorRecord record = recordsCaptor.getValue().get(0);
        assertThat(record.text()).isEqualTo("clean text");
        assertThat(record.metadata()).containsEntry("cleaned", true);
        assertThat(record.metadata()).containsEntry("cleanerPrompt", "rag-cleaner");
        assertThat(record.metadata()).containsEntry("originalTextLength", 10);
        assertThat(record.metadata()).containsEntry("indexedTextLength", 10);
        assertThat(record.metadata()).containsEntry("chunkCount", 1);
        assertThat(record.metadata()).containsEntry("chunkLength", 10);
    }

    @Test
    void shouldPreserveCallerMetadataWhenAddingCleanerMetadata() {
        RagIndexRequest request = new RagIndexRequest("doc-meta", "hello world", Map.of(
                "cleaned", "caller-cleaned",
                "cleanerPrompt", "caller-prompt",
                "originalTextLength", 999,
                "indexedTextLength", 888,
                "chunkCount", 777));
        when(textChunker.chunk("doc-meta", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-meta-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-meta-0", List.of(0.1, 0.2, 0.3)))));

        ragPipelineService.index(request);

        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        VectorRecord record = recordsCaptor.getValue().get(0);
        assertThat(record.metadata()).containsEntry("cleaned", "caller-cleaned");
        assertThat(record.metadata()).containsEntry("cleanerPrompt", "caller-prompt");
        assertThat(record.metadata()).containsEntry("originalTextLength", 999);
        assertThat(record.metadata()).containsEntry("indexedTextLength", 888);
        assertThat(record.metadata()).containsEntry("chunkCount", 777);
        assertThat(record.metadata()).containsEntry("chunkLength", 11);
    }

    @Test
    void shouldExtractKeywordsWithLlmWhenEnabled() {
        RagIndexRequest request = new RagIndexRequest("doc-kw", "hello world", Map.of(), List.of(), true);
        when(textChunker.chunk("doc-kw", "hello world"))
                .thenReturn(List.of(new TextChunk("doc-kw-0", "hello world")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("doc-kw-0", List.of(0.1, 0.2, 0.3)))));
        when(keywordExtractor.extract("hello world")).thenReturn(List.of(" hello ", "HELLO", "world", " "));

        ragPipelineService.index(request);

        verify(keywordExtractor).extract("hello world");
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        VectorRecord record = recordsCaptor.getValue().get(0);
        assertThat(record.metadata()).containsEntry("keywords", List.of("hello", "world"));
        assertThat(record.metadata()).containsEntry("keywordsText", "hello world");
        assertThat(record.metadata()).doesNotContainKeys("chunkKeywords", "chunkKeywordsText");
    }

    @Test
    void shouldUseChunkingOrchestratorAndReplaceObjectScopedChunks() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                chunkingOrchestrator,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                RagKeywordOptions.defaults());
        RagIndexRequest request = new RagIndexRequest("doc-orchestrated", "alpha beta", Map.of(
                "objectType", "attachment",
                "objectId", "42"));
        Chunk chunk = Chunk.of(
                "doc-orchestrated-0",
                "alpha beta",
                ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0)
                        .sourceDocumentId("doc-orchestrated")
                        .objectType("attachment")
                        .objectId("42")
                        .charCount(10)
                        .build());
        when(chunkingOrchestrator.chunk(any(ChunkingContext.class))).thenReturn(List.of(chunk));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        verify(textChunker, never()).chunk(anyString(), anyString());
        verify(vectorStorePort).replaceRecordsByObject(eq("attachment"), eq("42"), recordsCaptor.capture());
        VectorRecord record = recordsCaptor.getValue().get(0);
        assertThat(record.id()).isEqualTo("doc-orchestrated-0");
        assertThat(record.documentId()).isEqualTo("doc-orchestrated");
        assertThat(record.chunkId()).isEqualTo("doc-orchestrated-0");
        assertThat(record.embeddingDimension()).isEqualTo(2);
        assertThat(record.metadata())
                .containsEntry("sourceDocumentId", "doc-orchestrated")
                .containsEntry("strategy", "recursive")
                .containsEntry("objectType", "attachment")
                .containsEntry("objectId", "42")
                .containsEntry("chunkOrder", 0)
                .containsEntry("chunkIndex", 0);
    }

    @Test
    void shouldRouteConstructorWithChunkingOrchestratorWithoutLegacyTextChunker() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                null,
                chunkingOrchestrator,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                RagKeywordOptions.defaults());
        RagIndexRequest request = new RagIndexRequest("doc-orchestrated-null-legacy", "alpha beta", Map.of(
                "objectType", "attachment",
                "objectId", "42"));
        Chunk chunk = Chunk.of(
                "doc-orchestrated-null-legacy-0",
                "alpha beta",
                ChunkMetadata.builder(ChunkingStrategyType.RECURSIVE, 0)
                        .sourceDocumentId("doc-orchestrated-null-legacy")
                        .objectType("attachment")
                        .objectId("42")
                        .build());
        when(chunkingOrchestrator.chunk(any(ChunkingContext.class))).thenReturn(List.of(chunk));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        verify(vectorStorePort).replaceRecordsByObject(eq("attachment"), eq("42"), recordsCaptor.capture());
        assertThat(recordsCaptor.getValue().get(0).id()).isEqualTo("doc-orchestrated-null-legacy-0");
    }

    @Test
    void shouldRouteConstructorWithoutChunkingOrchestratorToLegacyTextChunker() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                null,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                RagKeywordOptions.defaults());
        RagIndexRequest request = new RagIndexRequest("doc-legacy", "alpha beta", Map.of());
        when(textChunker.chunk("doc-legacy", "alpha beta"))
                .thenReturn(List.of(new TextChunk("doc-legacy-0", "alpha beta")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        verify(textChunker).chunk("doc-legacy", "alpha beta");
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue().get(0).id()).isEqualTo("doc-legacy-0");
    }

    @Test
    void shouldDeleteObjectScopedChunksWhenNewIndexHasNoChunks() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                chunkingOrchestrator,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                RagKeywordOptions.defaults());
        RagIndexRequest request = new RagIndexRequest("doc-empty", "   ", Map.of(
                "objectType", "attachment",
                "objectId", "42"));

        ragPipelineService.index(request);

        verify(vectorStorePort).deleteByObject("attachment", "42");
        verify(vectorStorePort, never()).upsert(org.mockito.ArgumentMatchers.<List<VectorDocument>>any());
        verify(vectorStorePort, never()).upsertAll(org.mockito.ArgumentMatchers.<List<VectorRecord>>any());
        verify(vectorStorePort, never()).replaceByObject(anyString(), anyString(), any());
        verify(vectorStorePort, never()).replaceRecordsByObject(anyString(), anyString(), any());
    }

    @Test
    void shouldAddChunkKeywordsWhenScopeIsChunk() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.CHUNK, 4_000, true, 10));
        RagIndexRequest request = new RagIndexRequest("doc-chunk", "alpha beta", Map.of(), List.of(), true);
        when(textChunker.chunk("doc-chunk", "alpha beta"))
                .thenReturn(List.of(
                        new TextChunk("doc-chunk-0", "alpha"),
                        new TextChunk("doc-chunk-1", "beta")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));
        when(keywordExtractor.extract("alpha")).thenReturn(List.of("Alpha", "first"));
        when(keywordExtractor.extract("beta")).thenReturn(List.of("Beta", "second"));

        ragPipelineService.index(request);

        verify(keywordExtractor).extract("alpha");
        verify(keywordExtractor).extract("beta");
        verify(keywordExtractor, never()).extract("alpha beta");
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        List<VectorRecord> records = recordsCaptor.getValue();
        assertThat(records.get(0).metadata())
                .containsEntry("chunkKeywords", List.of("Alpha", "first"))
                .containsEntry("chunkKeywordsText", "Alpha first")
                .doesNotContainKeys("keywords", "keywordsText");
        assertThat(records.get(1).metadata())
                .containsEntry("chunkKeywords", List.of("Beta", "second"))
                .containsEntry("chunkKeywordsText", "Beta second")
                .doesNotContainKeys("keywords", "keywordsText");
    }

    @Test
    void shouldIgnoreCallerKeywordsWhenScopeIsChunk() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.CHUNK, 4_000, true, 10));
        RagIndexRequest request = new RagIndexRequest(
                "doc-manual-chunk",
                "alpha beta",
                Map.of(),
                List.of("manual1", "manual2"),
                false);
        when(textChunker.chunk("doc-manual-chunk", "alpha beta"))
                .thenReturn(List.of(new TextChunk("doc-manual-chunk-0", "alpha beta")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));

        ragPipelineService.index(request);

        verify(keywordExtractor, never()).extract(anyString());
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue().get(0).metadata())
                .doesNotContainKeys("keywords", "keywordsText", "chunkKeywords", "chunkKeywordsText");
    }

    @Test
    void shouldAddDocumentAndChunkKeywordsWhenScopeIsBoth() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.BOTH, 4_000, true, 10));
        RagIndexRequest request = new RagIndexRequest("doc-both", "alpha", Map.of(), List.of(), true);
        when(textChunker.chunk("doc-both", "alpha"))
                .thenReturn(List.of(new TextChunk("doc-both-0", "alpha")));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("chunk", List.of(0.1, 0.2)))));
        when(keywordExtractor.extract("alpha"))
                .thenReturn(List.of("document"))
                .thenReturn(List.of("chunk"));

        ragPipelineService.index(request);

        verify(keywordExtractor, times(2)).extract("alpha");
        verify(vectorStorePort).upsertAll(recordsCaptor.capture());
        VectorRecord record = recordsCaptor.getValue().get(0);
        assertThat(record.metadata()).containsEntry("keywords", List.of("document"));
        assertThat(record.metadata()).containsEntry("keywordsText", "document");
        assertThat(record.metadata()).containsEntry("chunkKeywords", List.of("chunk"));
        assertThat(record.metadata()).containsEntry("chunkKeywordsText", "chunk");
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
    void shouldSearchWithMetadataFilterUsingObjectScopedRetrievalPath() {
        RagSearchRequest request = new RagSearchRequest(
                "hello",
                2,
                MetadataFilter.objectScope("attachment", "42"));
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearchByObject(
                eq("hello"),
                eq("attachment"),
                eq("42"),
                any(VectorSearchRequest.class),
                anyDouble(),
                anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-filtered", "chunk", Map.of("objectType", "attachment", "objectId", "42"), List.of()),
                        0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results)
                .extracting(RagSearchResult::documentId)
                .containsExactly("doc-filtered");
        verify(vectorStorePort).hybridSearchByObject(
                eq("hello"),
                eq("attachment"),
                eq("42"),
                org.mockito.ArgumentMatchers.argThat(searchRequest ->
                        searchRequest.metadataFilter().hasObjectScope()
                                && "attachment".equals(searchRequest.metadataFilter().objectType())
                                && "42".equals(searchRequest.metadataFilter().objectId())),
                anyDouble(),
                anyDouble());
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
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
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
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
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
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
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
    void shouldSkipQueryExpansionWhenDisabled() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.DOCUMENT, 4_000, false, 10));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic", "semantic", Map.of(), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        verify(keywordExtractor, never()).extract(anyString());
        verify(vectorStorePort, times(1)).hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldLimitQueryExpansionKeywords() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.DOCUMENT, 4_000, true, 2));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of("alpha", "beta", "gamma"));
        when(vectorStorePort.hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(vectorStorePort.hybridSearch(eq("hello alpha beta"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-expanded", "expanded", Map.of(), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        verify(vectorStorePort).hybridSearch(eq("hello alpha beta"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldNormalizeExtractorKeywordsForQueryExpansion() {
        ragPipelineService = DefaultRagPipelineService.create(
                embeddingPort,
                vectorStorePort,
                textChunker,
                cache,
                retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                RagPipelineDiagnosticsOptions.defaults(),
                new RagKeywordOptions(RagKeywordOptions.KeywordScope.DOCUMENT, 4_000, true, 2));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of(" hello ", " Alpha ", "alpha", " beta "));
        when(vectorStorePort.hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(vectorStorePort.hybridSearch(eq("hello Alpha beta"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-expanded", "expanded", Map.of(), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        verify(vectorStorePort).hybridSearch(eq("hello Alpha beta"), any(VectorSearchRequest.class), anyDouble(), anyDouble());
    }

    @Test
    void shouldRejectInvalidKeywordScopeWithValidValuesMessage() {
        assertThatThrownBy(() -> RagKeywordOptions.KeywordScope.from("document-scope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valid values are: DOCUMENT, CHUNK, BOTH");
    }

    @Test
    void shouldSkipSemanticFallbackWhenDisabled() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
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
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
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

    @Test
    void shouldNotExposeDiagnosticsWhenDisabled() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-1", "chunk", Map.of(), List.of(0.5, 0.6)), 0.9)));

        ragPipelineService.search(request);

        assertThat(ragPipelineService.latestDiagnostics()).isEmpty();
    }

    @Test
    void shouldTrackHybridRetrievalDiagnosticsWhenEnabled() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor,
                null,
                new RagPipelineOptions(0.2d, 0.8d, 0.4d, true, true, 20, 100),
                new RagPipelineDiagnosticsOptions(true, false, 120));
        RagSearchRequest request = new RagSearchRequest("hello", 7);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), eq(0.2d), eq(0.8d)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-1", "chunk", Map.of(), List.of(0.5, 0.6)), 0.9)));

        ragPipelineService.search(request);

        assertThat(ragPipelineService.latestDiagnostics()).hasValueSatisfying(diagnostics -> {
            assertThat(diagnostics.strategy()).isEqualTo(RagRetrievalDiagnostics.Strategy.HYBRID);
            assertThat(diagnostics.initialResultCount()).isEqualTo(1);
            assertThat(diagnostics.finalResultCount()).isEqualTo(1);
            assertThat(diagnostics.minScore()).isEqualTo(0.4d);
            assertThat(diagnostics.vectorWeight()).isEqualTo(0.2d);
            assertThat(diagnostics.lexicalWeight()).isEqualTo(0.8d);
            assertThat(diagnostics.objectType()).isNull();
            assertThat(diagnostics.objectId()).isNull();
            assertThat(diagnostics.topK()).isEqualTo(7);
        });
    }

    @Test
    void shouldTrackKeywordFallbackDiagnosticsWhenEnabled() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                new RagPipelineDiagnosticsOptions(true, false, 120));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of("greeting"));
        when(vectorStorePort.hybridSearch(eq("hello"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));
        when(vectorStorePort.hybridSearch(eq("hello greeting"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-strong", "strong", Map.of(), List.of(0.5, 0.6)), 0.9)));

        ragPipelineService.search(request);

        assertThat(ragPipelineService.latestDiagnostics()).hasValueSatisfying(diagnostics -> {
            assertThat(diagnostics.strategy()).isEqualTo(RagRetrievalDiagnostics.Strategy.KEYWORD_ENRICHED_HYBRID);
            assertThat(diagnostics.initialResultCount()).isEqualTo(1);
            assertThat(diagnostics.finalResultCount()).isEqualTo(1);
        });
    }

    @Test
    void shouldTrackObjectScopedSemanticFallbackDiagnosticsWhenEnabled() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                new RagPipelineDiagnosticsOptions(true, false, 120));
        RagSearchRequest request = new RagSearchRequest("hello", 3);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of());
        when(vectorStorePort.hybridSearchByObject(anyString(), eq("attachment"), eq("42"), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of());
        when(vectorStorePort.searchByObject(eq("attachment"), eq("42"), any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic", "semantic", Map.of(), List.of(0.5, 0.6)), 0.9)));

        ragPipelineService.searchByObject(request, "attachment", "42");

        assertThat(ragPipelineService.latestDiagnostics()).hasValueSatisfying(diagnostics -> {
            assertThat(diagnostics.strategy()).isEqualTo(RagRetrievalDiagnostics.Strategy.SEMANTIC);
            assertThat(diagnostics.objectType()).isEqualTo("attachment");
            assertThat(diagnostics.objectId()).isEqualTo("42");
            assertThat(diagnostics.topK()).isEqualTo(3);
        });
    }

    @Test
    void shouldTrackNoneDiagnosticsWhenNoRelevantResultsRemain() {
        ragPipelineService = DefaultRagPipelineService.create(embeddingPort, vectorStorePort, textChunker, cache, retry,
                keywordExtractor,
                null,
                RagPipelineOptions.defaults(),
                new RagPipelineDiagnosticsOptions(true, false, 120));
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(keywordExtractor.extract("hello")).thenReturn(List.of());
        when(vectorStorePort.hybridSearch(anyString(), any(VectorSearchRequest.class), anyDouble(), anyDouble()))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-low", "weak", Map.of(), List.of(0.5, 0.6)), 0.01)));
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(
                        new VectorDocument("doc-semantic-low", "weak semantic", Map.of(), List.of(0.5, 0.6)), 0.01)));

        ragPipelineService.search(request);

        assertThat(ragPipelineService.latestDiagnostics()).hasValueSatisfying(diagnostics -> {
            assertThat(diagnostics.strategy()).isEqualTo(RagRetrievalDiagnostics.Strategy.NONE);
            assertThat(diagnostics.initialResultCount()).isEqualTo(1);
            assertThat(diagnostics.finalResultCount()).isZero();
        });
    }

    @Test
    void shouldNotIncludeSensitiveContentInDiagnosticsMetadata() {
        RagRetrievalDiagnostics diagnostics = new RagRetrievalDiagnostics(
                RagRetrievalDiagnostics.Strategy.HYBRID,
                1,
                1,
                0.15d,
                0.7d,
                0.3d,
                "attachment",
                "42",
                3);

        assertThat(diagnostics.toMetadata())
                .containsEntry("strategy", "hybrid")
                .doesNotContainKeys("content", "snippet", "text", "chunk");
    }

    private static final class RecordingProgressListener implements RagIndexProgressListener {

        private final List<studio.one.platform.ai.core.rag.RagIndexJobStep> steps = new ArrayList<>();
        private final List<Integer> chunkCounts = new ArrayList<>();
        private final List<Integer> embeddedCounts = new ArrayList<>();
        private final List<Integer> indexedCounts = new ArrayList<>();

        @Override
        public void onStep(studio.one.platform.ai.core.rag.RagIndexJobStep step) {
            steps.add(step);
        }

        @Override
        public void onChunkCount(int chunkCount) {
            chunkCounts.add(chunkCount);
        }

        @Override
        public void onEmbeddedCount(int embeddedCount) {
            embeddedCounts.add(embeddedCount);
        }

        @Override
        public void onIndexedCount(int indexedCount) {
            indexedCounts.add(indexedCount);
        }
    }

    private static final class CapturingVectorStore implements VectorStorePort {

        private final List<VectorDocument> upsertedDocuments = new ArrayList<>();

        @Override
        public void upsert(List<VectorDocument> documents) {
            upsertedDocuments.addAll(documents);
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return List.of();
        }

        @Override
        public boolean exists(String id, String contentHash) {
            return false;
        }

        private List<VectorDocument> upsertedDocuments() {
            return upsertedDocuments;
        }
    }
}
