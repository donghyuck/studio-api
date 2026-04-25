package studio.one.platform.ai.service.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;

import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.MetadataFilter;
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
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.cleaning.TextCleaner;
import studio.one.platform.ai.service.cleaning.TextCleaningResult;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.chunking.core.ChunkingOrchestrator;

@Slf4j
@SuppressWarnings("deprecation")
public class DefaultRagPipelineService implements RagPipelineService {

    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final RagChunker ragChunker;
    private final Cache<String, List<Double>> embeddingCache;
    private final Retry retry;
    private final KeywordExtractor keywordExtractor;
    private final TextCleaner textCleaner;
    private final RagPipelineOptions options;
    private final RagPipelineDiagnosticsOptions diagnosticsOptions;
    private final RagKeywordOptions keywordOptions;
    private final ThreadLocal<RagRetrievalDiagnostics> latestDiagnostics = new ThreadLocal<>();

    public DefaultRagPipelineService(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions,
            RagKeywordOptions keywordOptions) {
        this(embeddingPort, vectorStorePort, createChunker(textChunker, null), embeddingCache, retry, keywordExtractor, textCleaner,
                options, diagnosticsOptions, keywordOptions);
    }

    public DefaultRagPipelineService(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            ChunkingOrchestrator chunkingOrchestrator,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions,
            RagKeywordOptions keywordOptions) {
        this(embeddingPort, vectorStorePort, createChunker(textChunker, chunkingOrchestrator), embeddingCache, retry,
                keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions);
    }

    private DefaultRagPipelineService(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            RagChunker ragChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions,
            RagKeywordOptions keywordOptions) {

        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.vectorStorePort = Objects.requireNonNull(vectorStorePort, "vectorStorePort");
        this.ragChunker = Objects.requireNonNull(ragChunker, "ragChunker");
        this.embeddingCache = Objects.requireNonNull(embeddingCache, "embeddingCache");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.keywordExtractor = keywordExtractor;
        this.textCleaner = textCleaner;
        this.options = Objects.requireNonNull(options, "options");
        this.diagnosticsOptions = Objects.requireNonNull(diagnosticsOptions, "diagnosticsOptions");
        this.keywordOptions = Objects.requireNonNull(keywordOptions, "keywordOptions");
    }

    public static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor) {
        return create(embeddingPort, vectorStorePort, textChunker, embeddingCache, retry, keywordExtractor,
                null, RagPipelineOptions.defaults(), RagPipelineDiagnosticsOptions.defaults(),
                RagKeywordOptions.defaults());
    }

    public static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            RagPipelineOptions options) {
        return create(embeddingPort, vectorStorePort, textChunker, embeddingCache, retry, keywordExtractor, null,
                options, RagPipelineDiagnosticsOptions.defaults(), RagKeywordOptions.defaults());
    }

    public static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options) {
        return create(embeddingPort, vectorStorePort, textChunker, embeddingCache, retry, keywordExtractor,
                textCleaner, options, RagPipelineDiagnosticsOptions.defaults(), RagKeywordOptions.defaults());
    }

    static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions) {
        return create(embeddingPort, vectorStorePort, textChunker, embeddingCache, retry, keywordExtractor,
                textCleaner, options, diagnosticsOptions, RagKeywordOptions.defaults());
    }

    public static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions,
            RagKeywordOptions keywordOptions) {
        return create(embeddingPort, vectorStorePort, textChunker, null, embeddingCache, retry,
                keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions);
    }

    public static DefaultRagPipelineService create(EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            TextChunker textChunker,
            ChunkingOrchestrator chunkingOrchestrator,
            Cache<String, List<Double>> embeddingCache,
            Retry retry,
            KeywordExtractor keywordExtractor,
            TextCleaner textCleaner,
            RagPipelineOptions options,
            RagPipelineDiagnosticsOptions diagnosticsOptions,
            RagKeywordOptions keywordOptions) {
        return new DefaultRagPipelineService(embeddingPort, vectorStorePort, textChunker, chunkingOrchestrator,
                embeddingCache, retry, keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions);
    }

    private static RagChunker createChunker(TextChunker textChunker, ChunkingOrchestrator chunkingOrchestrator) {
        if (chunkingOrchestrator != null) {
            return new OrchestratedRagChunker(chunkingOrchestrator);
        }
        return new LegacyTextChunkerAdapter(textChunker);
    }

    @Override
    public void index(RagIndexRequest request) {

        log.debug("using llm keywords extract : {}", request.useLlmKeywordExtraction());

        TextCleaningResult cleaning = cleanText(request.text());
        String indexedText = cleaning.text() == null ? request.text() : cleaning.text();
        List<RagPipelineChunk> chunks = chunk(indexedText, request);
        List<VectorDocument> documents = new ArrayList<>(chunks.size());
        List<String> documentKeywords = keywordOptions.scope().includesDocument()
                ? resolveDocumentKeywords(request, indexedText)
                : List.of();
        Map<String, Object> baseMetadata = new HashMap<>(request.metadata());
        baseMetadata.putIfAbsent("cleaned", cleaning.cleaned());
        baseMetadata.putIfAbsent("cleanerPrompt", cleaning.cleanerPrompt() == null ? "" : cleaning.cleanerPrompt());
        baseMetadata.putIfAbsent("originalTextLength", request.text().length());
        baseMetadata.putIfAbsent("indexedTextLength", indexedText.length());
        baseMetadata.putIfAbsent("chunkCount", chunks.size());
        if (!documentKeywords.isEmpty()) {
            baseMetadata.put("keywords", documentKeywords);
            baseMetadata.put("keywordsText", String.join(" ", documentKeywords));
        }
        int order = 0;
        for (RagPipelineChunk chunk : chunks) {
            List<Double> embedding = embedWithCache(chunk.content());
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            mergeChunkMetadata(metadata, chunk.metadata());
            List<String> chunkKeywords = keywordOptions.scope().includesChunk()
                    ? resolveChunkKeywords(request, chunk.content())
                    : List.of();
            if (!chunkKeywords.isEmpty()) {
                metadata.put("chunkKeywords", chunkKeywords);
                metadata.put("chunkKeywordsText", String.join(" ", chunkKeywords));
            }
            metadata.put("documentId", request.documentId());
            metadata.put("chunkId", chunk.id());
            metadata.put("chunkOrder", order++);
            metadata.put("chunkLength", chunk.content().length());
            documents.add(new VectorDocument(chunk.id(), chunk.content(), metadata, embedding));
        }
        String objectType = RagChunkingMetadata.normalizeObjectScope(baseMetadata.get("objectType"));
        String objectId = RagChunkingMetadata.normalizeObjectScope(baseMetadata.get("objectId"));
        if (objectType != null && objectId != null) {
            if (documents.isEmpty()) {
                vectorStorePort.deleteByObject(objectType, objectId);
            } else {
                vectorStorePort.replaceByObject(objectType, objectId, documents);
            }
        } else if (!documents.isEmpty()) {
            vectorStorePort.upsert(documents);
        }
    }

    private List<RagPipelineChunk> chunk(String indexedText, RagIndexRequest request) {
        if (indexedText == null || indexedText.isBlank()) {
            return List.of();
        }
        return ragChunker.chunk(indexedText, request);
    }

    private void mergeChunkMetadata(Map<String, Object> metadata, Map<String, Object> chunkMetadata) {
        chunkMetadata.forEach((key, value) -> {
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                metadata.putIfAbsent(key, value);
            }
        });
    }

    @Override
    public List<RagSearchResult> search(RagSearchRequest request) {
        clearDiagnostics();
        MetadataFilter filter = request.metadataFilter();
        if (filter.hasObjectScope()) {
            return searchObjectScope(request.query(), request.topK(), filter);
        }
        List<Double> queryEmbedding = embedWithCache(request.query());
        VectorSearchRequest searchRequest = new VectorSearchRequest(queryEmbedding, request.topK(), filter);
        List<VectorSearchResult> results = searchWithFallback(
                request.query(),
                searchRequest,
                query -> vectorStorePort.hybridSearch(query, searchRequest, options.vectorWeight(), options.lexicalWeight()),
                () -> vectorStorePort.search(searchRequest),
                null,
                null);
        return toRagSearchResults(results);
    }

    @Override
    public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
        clearDiagnostics();
        MetadataFilter filter = MetadataFilter.objectScope(objectType, objectId);
        if (filter.isEmpty()) {
            filter = request.metadataFilter();
        }
        return searchObjectScope(request.query(), request.topK(), filter);
    }

    private List<RagSearchResult> searchObjectScope(String queryText, int topK, MetadataFilter filter) {
        List<Double> queryEmbedding = embedWithCache(queryText);
        VectorSearchRequest searchRequest = new VectorSearchRequest(
                queryEmbedding,
                topK,
                filter);
        List<VectorSearchResult> results = searchWithFallback(
                queryText,
                searchRequest,
                query -> vectorStorePort.hybridSearchByObject(
                        query,
                        filter.objectType(),
                        filter.objectId(),
                        searchRequest,
                        options.vectorWeight(),
                        options.lexicalWeight()),
                () -> vectorStorePort.searchByObject(filter.objectType(), filter.objectId(), searchRequest),
                filter.objectType(),
                filter.objectId());
        return toRagSearchResults(results);
    }

    @Override
    public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        clearDiagnostics();
        List<VectorSearchResult> results = vectorStorePort.listByObject(objectType, objectId, options.clampListLimit(limit));
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    @Override
    public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
        return diagnosticsOptions.enabled()
                ? Optional.ofNullable(latestDiagnostics.get())
                : Optional.empty();
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

    private TextCleaningResult cleanText(String text) {
        if (textCleaner == null) {
            return TextCleaningResult.skipped(text);
        }
        return textCleaner.clean(text);
    }

    private List<String> resolveDocumentKeywords(RagIndexRequest request, String indexedText) {
        if (request.keywords() != null && !request.keywords().isEmpty()) {
            return normalizeKeywords(request.keywords());
        }
        if (!request.useLlmKeywordExtraction() || keywordExtractor == null) {
            return List.of();
        }
        try {
            return normalizeKeywords(keywordExtractor.extract(indexedText));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> resolveChunkKeywords(RagIndexRequest request, String chunkText) {
        if (!request.useLlmKeywordExtraction() || keywordExtractor == null) {
            return List.of();
        }
        try {
            return normalizeKeywords(keywordExtractor.extract(chunkText));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<VectorSearchResult> searchWithFallback(
            String query,
            VectorSearchRequest searchRequest,
            Function<String, List<VectorSearchResult>> hybridSearch,
            Supplier<List<VectorSearchResult>> semanticSearch,
            String objectType,
            String objectId) {
        List<VectorSearchResult> results = hybridSearch.apply(query);
        if (hasRelevantResults(results)) {
            recordDiagnostics(RagRetrievalDiagnostics.Strategy.HYBRID,
                    results.size(), results.size(), searchRequest, objectType, objectId, results);
            return results;
        }
        int initialResultCount = safeSize(results);

        String enrichedQuery = options.keywordFallbackEnabled() ? enrichQuery(query) : query;
        if (options.keywordFallbackEnabled() && !enrichedQuery.equals(query)) {
            List<VectorSearchResult> enrichedResults = hybridSearch.apply(enrichedQuery);
            if (hasRelevantResults(enrichedResults)) {
                recordDiagnostics(RagRetrievalDiagnostics.Strategy.KEYWORD_ENRICHED_HYBRID,
                        initialResultCount, enrichedResults.size(), searchRequest, objectType, objectId, enrichedResults);
                return enrichedResults;
            }
        }

        if (options.semanticFallbackEnabled()) {
            List<VectorSearchResult> semanticResults = semanticSearch.get();
            if (hasRelevantResults(semanticResults)) {
                recordDiagnostics(RagRetrievalDiagnostics.Strategy.SEMANTIC,
                        initialResultCount, semanticResults.size(), searchRequest, objectType, objectId, semanticResults);
                return semanticResults;
            }
        }
        recordDiagnostics(RagRetrievalDiagnostics.Strategy.NONE,
                initialResultCount, 0, searchRequest, objectType, objectId, List.of());
        return List.of();
    }

    private String enrichQuery(String query) {
        if (keywordExtractor == null || !keywordOptions.queryExpansionEnabled()) {
            return query;
        }
        try {
            List<String> extracted = normalizeKeywords(keywordExtractor.extract(query));
            if (extracted == null || extracted.isEmpty()) {
                return query;
            }
            List<String> uniqueTerms = new ArrayList<>();
            uniqueTerms.add(query.trim());
            for (String keyword : extracted) {
                if (uniqueTerms.stream().noneMatch(keyword::equalsIgnoreCase)) {
                    uniqueTerms.add(keyword);
                }
                if (uniqueTerms.size() > keywordOptions.queryExpansionMaxKeywords()) {
                    break;
                }
            }
            return String.join(" ", uniqueTerms);
        } catch (Exception ex) {
            log.debug("Failed to extract keywords for RAG search fallback. query={}", query, ex);
            return query;
        }
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String trimmed = keyword.trim();
            if (normalized.stream().noneMatch(trimmed::equalsIgnoreCase)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private boolean hasRelevantResults(List<VectorSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(result -> result.score() >= options.minRelevanceScore());
    }

    private List<RagSearchResult> toRagSearchResults(List<VectorSearchResult> results) {
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    private void clearDiagnostics() {
        latestDiagnostics.remove();
    }

    private void recordDiagnostics(
            RagRetrievalDiagnostics.Strategy strategy,
            int initialResultCount,
            int finalResultCount,
            VectorSearchRequest searchRequest,
            String objectType,
            String objectId,
            List<VectorSearchResult> results) {
        if (!diagnosticsOptions.enabled()) {
            return;
        }
        RagRetrievalDiagnostics diagnostics = new RagRetrievalDiagnostics(
                strategy,
                initialResultCount,
                finalResultCount,
                options.minRelevanceScore(),
                options.vectorWeight(),
                options.lexicalWeight(),
                objectType,
                objectId,
                searchRequest.topK());
        latestDiagnostics.set(diagnostics);
        logDiagnostics(diagnostics, results);
    }

    private void logDiagnostics(RagRetrievalDiagnostics diagnostics, List<VectorSearchResult> results) {
        if (!diagnosticsOptions.logResults() || !log.isDebugEnabled()) {
            return;
        }
        log.debug("RAG retrieval diagnostics strategy={}, initialResultCount={}, finalResultCount={}, minScore={}, "
                        + "vectorWeight={}, lexicalWeight={}, objectType={}, objectId={}, topK={}",
                diagnostics.strategy().value(),
                diagnostics.initialResultCount(),
                diagnostics.finalResultCount(),
                diagnostics.minScore(),
                diagnostics.vectorWeight(),
                diagnostics.lexicalWeight(),
                diagnostics.objectType(),
                diagnostics.objectId(),
                diagnostics.topK());
        results.stream().limit(diagnostics.topK()).forEach(result ->
                log.debug("RAG diagnostic hit docId={}, score={}, snippet={}",
                        result.document().id(),
                        String.format("%.3f", result.score()),
                        truncate(result.document().content(), diagnosticsOptions.maxSnippetChars())));
    }

    private int safeSize(List<VectorSearchResult> results) {
        return results == null ? 0 : results.size();
    }

    private String truncate(String content, int maxLen) {
        if (content == null || maxLen == 0) {
            return "";
        }
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen);
    }
}
