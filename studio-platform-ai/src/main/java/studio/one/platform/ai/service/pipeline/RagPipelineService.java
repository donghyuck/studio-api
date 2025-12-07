package studio.one.platform.ai.service.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;

import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
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
import studio.one.platform.constant.ServiceNames;

@Service(RagPipelineService.SERVICE_NAME)
@ConditionalOnProperty(prefix = "studio.ai", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class RagPipelineService {
    public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":ai:rag-pipelien-service";

    private static final double HYBRID_VECTOR_WEIGHT = 0.7;
    private static final double HYBRID_LEXICAL_WEIGHT = 0.3;

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final TextChunker textChunker;
    private final Cache<String, List<Double>> embeddingCache;
    private final Retry retry;
    private final KeywordExtractor keywordExtractor;

    public RagPipelineService(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor) {

        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.vectorStorePort = Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker");
        this.embeddingCache = Objects.requireNonNull(embeddingCache, "embeddingCache");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.keywordExtractor = keywordExtractor;
    }

    public void index(RagIndexRequest request) {

        log.debug("using llm keywords extract : {}", request.useLlmKeywordExtraction());

        List<TextChunk> chunks = textChunker.chunk(request.documentId(), request.text());
        List<VectorDocument> documents = new ArrayList<>(chunks.size());
        List<String> keywords = resolveKeywords(request);
        Map<String, Object> baseMetadata = new HashMap<>(request.metadata());
        if (!keywords.isEmpty()) {
            baseMetadata.put("keywords", keywords);
            baseMetadata.put("keywordsText", String.join(" ", keywords));
        }
        int order = 0;
        for (TextChunk chunk : chunks) {
            List<Double> embedding = embedWithCache(chunk.content());
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("documentId", request.documentId());
            metadata.put("chunkId", chunk.id());
            metadata.put("chunkOrder", order++);
            documents.add(new VectorDocument(chunk.id(), chunk.content(), metadata, embedding));
        }
        if (!documents.isEmpty()) {
            vectorStorePort.upsert(documents);
        }
    }

    public List<RagSearchResult> search(RagSearchRequest request) {
        List<Double> queryEmbedding = embedWithCache(request.query());
        VectorSearchRequest searchRequest = new VectorSearchRequest(queryEmbedding, request.topK());
        List<VectorSearchResult> results = vectorStorePort.hybridSearch(
                request.query(),
                searchRequest,
                HYBRID_VECTOR_WEIGHT,
                HYBRID_LEXICAL_WEIGHT);
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
        List<Double> queryEmbedding = embedWithCache(request.query());
        VectorSearchRequest searchRequest = new VectorSearchRequest(queryEmbedding, request.topK());
        List<VectorSearchResult> results = vectorStorePort.hybridSearchByObject(
                request.query(),
                objectType,
                objectId,
                searchRequest,
                HYBRID_VECTOR_WEIGHT,
                HYBRID_LEXICAL_WEIGHT);
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        List<VectorSearchResult> results = vectorStorePort.listByObject(objectType, objectId, limit);
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    private List<Double> embedWithCache(String text) {
        List<Double> cached = embeddingCache.getIfPresent(text);
        if (cached != null) {
            return cached;
        }
        EmbeddingResponse response = executeEmbedding(List.of(text));
        EmbeddingVector vector = response.vectors().get(0);
        List<Double> values = List.copyOf(vector.values());
        embeddingCache.put(text, values);
        return values;
    }

    private EmbeddingResponse executeEmbedding(List<String> texts) {
        Supplier<EmbeddingResponse> supplier = () -> embeddingPort.embed(new EmbeddingRequest(texts));
        return Retry.decorateSupplier(retry, supplier).get();
    }

    private List<String> resolveKeywords(RagIndexRequest request) {
        if (request.keywords() != null && !request.keywords().isEmpty()) {
            return request.keywords();
        }
        if (!request.useLlmKeywordExtraction() || keywordExtractor == null) {
            return List.of();
        }
        try {
            List<String> extracted = keywordExtractor.extract(request.text());
            return extracted == null ? List.of() : extracted;
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
