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
import org.mockito.InjectMocks;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagPipelineServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private TextChunker textChunker;

    private Cache<String, List<Double>> cache;
    private Retry retry;

    @InjectMocks
    private RagPipelineService ragPipelineService;

    @Captor
    private ArgumentCaptor<List<VectorDocument>> documentsCaptor;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().build();
        retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build());
        ragPipelineService = new RagPipelineService(embeddingPort, vectorStorePort, textChunker, cache, retry);
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
    void shouldSearchAndMapResults() {
        RagSearchRequest request = new RagSearchRequest("hello", 2);
        when(embeddingPort.embed(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("hello", List.of(0.5, 0.6)))));
        when(vectorStorePort.search(any(VectorSearchRequest.class)))
                .thenReturn(List.of(new VectorSearchResult(new VectorDocument("doc-1", "chunk", Map.of("author", "test"), List.of(0.5, 0.6)), 0.9)));

        List<RagSearchResult> results = ragPipelineService.search(request);

        assertThat(results).hasSize(1);
        RagSearchResult result = results.get(0);
        assertThat(result.documentId()).isEqualTo("doc-1");
        assertThat(result.metadata()).containsEntry("author", "test");
        assertThat(result.score()).isEqualTo(0.9);
    }
}
