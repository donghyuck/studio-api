package studio.one.platform.ai.service.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStep;
import studio.one.platform.ai.core.rag.RagRetrievalDiagnostics;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.vector.VectorRecord;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.cleaning.TextCleaner;
import studio.one.platform.ai.service.cleaning.TextCleaningResult;
import studio.one.platform.ai.service.keyword.KeywordExtractor;
import studio.one.platform.chunking.core.ChunkMetadata;
import studio.one.platform.chunking.core.ChunkType;
import studio.one.platform.chunking.core.ChunkingOrchestrator;

@Slf4j
@SuppressWarnings("deprecation")
public class DefaultRagPipelineService implements RagPipelineService {

    private final EmbeddingPort embeddingPort;
    private final RagEmbeddingProfileResolver embeddingProfileResolver;
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
                options, diagnosticsOptions, keywordOptions, new SinglePortRagEmbeddingProfileResolver(embeddingPort));
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
                keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions,
                new SinglePortRagEmbeddingProfileResolver(embeddingPort));
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
            RagKeywordOptions keywordOptions,
            RagEmbeddingProfileResolver embeddingProfileResolver) {

        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.embeddingProfileResolver = Objects.requireNonNull(embeddingProfileResolver, "embeddingProfileResolver");
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
                keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions,
                new SinglePortRagEmbeddingProfileResolver(embeddingPort));
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
        return create(embeddingPort, vectorStorePort, textChunker, chunkingOrchestrator,
                embeddingCache, retry, keywordExtractor, textCleaner, options, diagnosticsOptions,
                keywordOptions, new SinglePortRagEmbeddingProfileResolver(embeddingPort));
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
            RagKeywordOptions keywordOptions,
            RagEmbeddingProfileResolver embeddingProfileResolver) {
        return new DefaultRagPipelineService(embeddingPort, vectorStorePort, createChunker(textChunker, chunkingOrchestrator),
                embeddingCache, retry, keywordExtractor, textCleaner, options, diagnosticsOptions, keywordOptions,
                embeddingProfileResolver);
    }

    private static RagChunker createChunker(TextChunker textChunker, ChunkingOrchestrator chunkingOrchestrator) {
        if (chunkingOrchestrator != null) {
            return new OrchestratedRagChunker(chunkingOrchestrator);
        }
        return new LegacyTextChunkerAdapter(textChunker);
    }

    @Override
    public void index(RagIndexRequest request) {
        index(request, RagIndexProgressListener.noop());
    }

    @Override
    public void index(RagIndexRequest request, RagIndexProgressListener listener) {

        log.debug("using llm keywords extract : {}", request.useLlmKeywordExtraction());

        RagIndexProgressListener progress = listener == null ? RagIndexProgressListener.noop() : listener;
        TextCleaningResult cleaning = cleanText(request.text());
        String indexedText = cleaning.text() == null ? request.text() : cleaning.text();
        progress.onStep(RagIndexJobStep.CHUNKING);
        List<RagPipelineChunk> chunks = chunk(indexedText, request);
        progress.onChunkCount(chunks.size());
        List<VectorRecord> records = new ArrayList<>(chunks.size());
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
        progress.onStep(RagIndexJobStep.EMBEDDING);
        for (RagPipelineChunk chunk : chunks) {
            ResolvedRagEmbedding resolvedEmbedding = resolveEmbedding(request, chunk);
            List<Double> embedding = embedWithCache(chunk.content(), resolvedEmbedding);
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            mergeChunkMetadata(metadata, chunk.metadata());
            metadata.putAll(resolvedEmbedding.metadata());
            List<String> chunkKeywords = keywordOptions.scope().includesChunk()
                    ? resolveChunkKeywords(request, chunk.content())
                    : List.of();
            if (!chunkKeywords.isEmpty()) {
                metadata.put("chunkKeywords", chunkKeywords);
                metadata.put("chunkKeywordsText", String.join(" ", chunkKeywords));
            }
            metadata.put("documentId", request.documentId());
            metadata.put("chunkId", chunk.id());
            metadata.put("chunkOrder", order);
            metadata.put(VectorRecord.KEY_CHUNK_INDEX, order);
            metadata.put("chunkLength", chunk.content().length());
            records.add(vectorRecord(request.documentId(), chunk, metadata, embedding));
            progress.onEmbeddedCount(records.size());
            order++;
        }
        String objectType = RagChunkingMetadata.normalizeObjectScope(baseMetadata.get("objectType"));
        String objectId = RagChunkingMetadata.normalizeObjectScope(baseMetadata.get("objectId"));
        progress.onStep(RagIndexJobStep.INDEXING);
        if (objectType != null && objectId != null) {
            if (records.isEmpty()) {
                vectorStorePort.deleteByObject(objectType, objectId);
            } else {
                vectorStorePort.replaceRecordsByObject(objectType, objectId, records);
            }
        } else if (!records.isEmpty()) {
            vectorStorePort.upsertAll(records);
        }
        progress.onIndexedCount(records.size());
    }

    private VectorRecord vectorRecord(
            String documentId,
            RagPipelineChunk chunk,
            Map<String, Object> metadata,
            List<Double> embedding) {
        return VectorRecord.builder()
                .id(chunk.id())
                .documentId(documentId)
                .chunkId(chunk.id())
                .parentChunkId(text(metadata.get(VectorRecord.KEY_PARENT_CHUNK_ID)))
                .contentHash(contentHash(chunk.content()))
                .text(chunk.content())
                .embedding(embedding)
                .embeddingModel(embeddingModel(metadata))
                .embeddingDimension(embedding.size())
                .chunkType(text(metadata.get(VectorRecord.KEY_CHUNK_TYPE)))
                .headingPath(text(firstPresent(metadata, VectorRecord.KEY_HEADING_PATH, ChunkMetadata.KEY_SECTION)))
                .sourceRef(text(firstPresent(metadata,
                        VectorRecord.KEY_SOURCE_REF,
                        ChunkMetadata.KEY_SOURCE_REF,
                        ChunkMetadata.KEY_SOURCE_REFS)))
                .page(integer(metadata.get(VectorRecord.KEY_PAGE)))
                .slide(integer(metadata.get(VectorRecord.KEY_SLIDE)))
                .metadata(metadata)
                .build();
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

    private Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && (!(value instanceof String text) || !text.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private String embeddingModel(Map<String, Object> metadata) {
        String embeddingModel = text(metadata.get(VectorRecord.KEY_EMBEDDING_MODEL));
        return embeddingModel == null ? "unknown" : embeddingModel;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .filter(Objects::nonNull)
                    .map(Objects::toString)
                    .filter(text -> !text.isBlank())
                    .reduce((left, right) -> left + " > " + right)
                    .orElse(null);
        }
        String text = Objects.toString(value, null);
        return text == null || text.isBlank() ? null : text;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String contentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    @Override
    public List<RagSearchResult> search(RagSearchRequest request) {
        clearDiagnostics();
        request = withDefaults(request);
        MetadataFilter filter = request.metadataFilter();
        if (filter.hasObjectScope()) {
            return searchObjectScope(request, filter);
        }
        ResolvedRagEmbedding resolvedEmbedding = resolveEmbedding(request);
        List<Double> queryEmbedding = embedWithCache(request.query(), resolvedEmbedding);
        VectorSearchRequest searchRequest = new VectorSearchRequest(
                queryEmbedding,
                request.topK(),
                embeddingFilter(filter, resolvedEmbedding, hasEmbeddingSelection(request)),
                request.minScore());
        List<VectorSearchResult> results = searchWithFallback(
                request.query(),
                searchRequest,
                query -> vectorStorePort.hybridSearch(query, searchRequest, options.vectorWeight(), options.lexicalWeight()),
                () -> vectorStorePort.search(searchRequest),
                null,
                null,
                request.requestedTopK(),
                request.requestedMinScore());
        return toRagSearchResults(results);
    }

    @Override
    public List<RagSearchResult> searchByObject(RagSearchRequest request, String objectType, String objectId) {
        clearDiagnostics();
        request = withDefaults(request);
        MetadataFilter filter = MetadataFilter.objectScope(objectType, objectId);
        if (filter.isEmpty()) {
            filter = request.metadataFilter();
        }
        return searchObjectScope(request, filter);
    }

    private List<RagSearchResult> searchObjectScope(RagSearchRequest request, MetadataFilter filter) {
        ResolvedRagEmbedding resolvedEmbedding = resolveEmbedding(request);
        MetadataFilter searchFilter = embeddingFilter(filter, resolvedEmbedding, hasEmbeddingSelection(request));
        List<Double> queryEmbedding = embedWithCache(request.query(), resolvedEmbedding);
        VectorSearchRequest searchRequest = new VectorSearchRequest(
                queryEmbedding,
                request.topK(),
                searchFilter,
                request.minScore());
        List<VectorSearchResult> results = searchWithFallback(
                request.query(),
                searchRequest,
                query -> vectorStorePort.hybridSearchByObject(
                        query,
                        searchFilter.objectType(),
                        searchFilter.objectId(),
                        searchRequest,
                        options.vectorWeight(),
                        options.lexicalWeight()),
                () -> vectorStorePort.searchByObject(searchFilter.objectType(), searchFilter.objectId(), searchRequest),
                searchFilter.objectType(),
                searchFilter.objectId(),
                request.requestedTopK(),
                request.requestedMinScore());
        return toRagSearchResults(results);
    }

    @Override
    public List<RagSearchResult> listByObject(String objectType, String objectId, Integer limit) {
        clearDiagnostics();
        int effectiveLimit = options.clampListLimit(limit);
        List<VectorSearchResult> results = limitResults(
                vectorStorePort.listByObject(objectType, objectId, effectiveLimit),
                effectiveLimit);
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    @Override
    public List<RagSearchResult> listByObject(String objectType, String objectId, int offset, int limit) {
        clearDiagnostics();
        List<VectorSearchResult> results = vectorStorePort.listByObject(
                objectType,
                objectId,
                Math.max(0, offset),
                clampPagedListLimit(limit));
        return results.stream()
                .map(result -> new RagSearchResult(
                        result.document().id(),
                        result.document().content(),
                        result.document().metadata(),
                        result.score()))
                .toList();
    }

    private int clampPagedListLimit(int limit) {
        if (limit <= 0) {
            return options.defaultListLimit();
        }
        return Math.min(limit, options.maxListLimit() + 1);
    }

    @Override
    public Optional<RagRetrievalDiagnostics> latestDiagnostics() {
        return diagnosticsOptions.enabled()
                ? Optional.ofNullable(latestDiagnostics.get())
                : Optional.empty();
    }

    private List<Double> embedWithCache(String text) {
        return embedWithCache(text, resolveLegacyEmbedding());
    }

    private List<Double> embedWithCache(String text, ResolvedRagEmbedding resolvedEmbedding) {
        String cacheKey = embeddingCacheKey(text, resolvedEmbedding);
        List<Double> cached = embeddingCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        EmbeddingResponse response = executeEmbedding(List.of(text), resolvedEmbedding);
        EmbeddingVector vector = response.vectors().get(0);
        List<Double> values = List.copyOf(vector.values());
        embeddingCache.put(cacheKey, values);
        return values;
    }

    private EmbeddingResponse executeEmbedding(List<String> texts, ResolvedRagEmbedding resolvedEmbedding) {
        Supplier<EmbeddingResponse> supplier = () -> resolvedEmbedding.embeddingPort().embed(resolvedEmbedding.request(texts));
        try {
            return Retry.decorateSupplier(retry, supplier).get();
        } catch (RuntimeException ex) {
            if (AiProviderExceptionSupport.isQuotaOrRateLimit(ex)) {
                throw new EmbeddingProviderQuotaExceededException(
                        "Embedding provider quota exceeded while generating RAG embedding.",
                        ex);
            }
            throw ex;
        }
    }

    private ResolvedRagEmbedding resolveLegacyEmbedding() {
        return embeddingProfileResolver.resolve(new RagEmbeddingSelection(null, null, null, EmbeddingInputType.TEXT));
    }

    private ResolvedRagEmbedding resolveEmbedding(RagIndexRequest request, RagPipelineChunk chunk) {
        return embeddingProfileResolver.resolve(new RagEmbeddingSelection(
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                embeddingInputType(chunk.metadata())));
    }

    private ResolvedRagEmbedding resolveEmbedding(RagSearchRequest request) {
        return embeddingProfileResolver.resolve(new RagEmbeddingSelection(
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                EmbeddingInputType.TEXT));
    }

    private MetadataFilter embeddingFilter(
            MetadataFilter filter,
            ResolvedRagEmbedding resolvedEmbedding,
            boolean selectedByRequest) {
        if (!selectedByRequest || !hasResolvedEmbeddingMetadata(resolvedEmbedding)) {
            return filter;
        }
        Map<String, Object> equals = new HashMap<>(filter.equalsCriteria());
        if (resolvedEmbedding.model() != null) {
            equals.put(VectorRecord.KEY_EMBEDDING_MODEL, resolvedEmbedding.model());
        }
        if (resolvedEmbedding.dimension() != null) {
            equals.put(VectorRecord.KEY_EMBEDDING_DIMENSION, resolvedEmbedding.dimension());
        }
        if (resolvedEmbedding.provider() != null) {
            equals.put(VectorRecord.KEY_EMBEDDING_PROVIDER, resolvedEmbedding.provider());
        }
        if (resolvedEmbedding.profileId() != null) {
            equals.put(VectorRecord.KEY_EMBEDDING_PROFILE_ID, resolvedEmbedding.profileId());
        }
        return MetadataFilter.of(equals, filter.inCriteria(), filter.rangeCriteria());
    }

    private boolean hasResolvedEmbeddingMetadata(ResolvedRagEmbedding resolvedEmbedding) {
        return resolvedEmbedding.profileId() != null
                || resolvedEmbedding.provider() != null
                || resolvedEmbedding.model() != null
                || resolvedEmbedding.dimension() != null;
    }

    private boolean hasEmbeddingSelection(RagSearchRequest request) {
        return request.embeddingProfileId() != null
                || request.embeddingProvider() != null
                || request.embeddingModel() != null;
    }

    private String embeddingCacheKey(String text, ResolvedRagEmbedding resolvedEmbedding) {
        return String.join("|",
                text == null ? "" : text,
                resolvedEmbedding.profileId() == null ? "" : resolvedEmbedding.profileId(),
                resolvedEmbedding.provider() == null ? "" : resolvedEmbedding.provider(),
                resolvedEmbedding.model() == null ? "" : resolvedEmbedding.model(),
                resolvedEmbedding.inputType().name());
    }

    private EmbeddingInputType embeddingInputType(Map<String, Object> metadata) {
        if (metadata == null) {
            return EmbeddingInputType.TEXT;
        }
        ChunkType type = ChunkType.from(text(metadata.get(VectorRecord.KEY_CHUNK_TYPE)));
        if (type == ChunkType.TABLE) {
            return EmbeddingInputType.TABLE_TEXT;
        }
        if (type == ChunkType.IMAGE_CAPTION) {
            return EmbeddingInputType.IMAGE_CAPTION;
        }
        if (type == ChunkType.OCR) {
            return EmbeddingInputType.OCR_TEXT;
        }
        return EmbeddingInputType.TEXT;
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
            String objectId,
            Integer requestedTopK,
            Double requestedMinScore) {
        List<VectorSearchResult> rawResults = limitResults(hybridSearch.apply(query), searchRequest.topK());
        List<VectorSearchResult> results = applyMinScore(rawResults, searchRequest.minScore());
        List<VectorSearchResult> lastRawResults = rawResults;
        List<VectorSearchResult> lastResults = results;
        if (hasRelevantResults(rawResults)) {
            recordDiagnostics(RagRetrievalDiagnostics.Strategy.HYBRID,
                    rawResults.size(), results.size(), searchRequest, objectType, objectId,
                    requestedTopK, requestedMinScore, rawResults, results);
            return results;
        }
        int initialResultCount = safeSize(rawResults);

        String enrichedQuery = options.keywordFallbackEnabled() ? enrichQuery(query) : query;
        if (options.keywordFallbackEnabled() && !enrichedQuery.equals(query)) {
            List<VectorSearchResult> enrichedRawResults = limitResults(
                    hybridSearch.apply(enrichedQuery),
                    searchRequest.topK());
            List<VectorSearchResult> enrichedResults = applyMinScore(enrichedRawResults, searchRequest.minScore());
            lastRawResults = enrichedRawResults;
            lastResults = enrichedResults;
            if (hasRelevantResults(enrichedRawResults)) {
                recordDiagnostics(RagRetrievalDiagnostics.Strategy.KEYWORD_ENRICHED_HYBRID,
                        initialResultCount, enrichedResults.size(), searchRequest, objectType, objectId,
                        requestedTopK, requestedMinScore, enrichedRawResults, enrichedResults);
                return enrichedResults;
            }
        }

        if (options.semanticFallbackEnabled()) {
            List<VectorSearchResult> semanticRawResults = limitResults(semanticSearch.get(), searchRequest.topK());
            List<VectorSearchResult> semanticResults = applyMinScore(semanticRawResults, searchRequest.minScore());
            lastRawResults = semanticRawResults;
            lastResults = semanticResults;
            if (hasRelevantResults(semanticRawResults)) {
                recordDiagnostics(RagRetrievalDiagnostics.Strategy.SEMANTIC,
                        initialResultCount, semanticResults.size(), searchRequest, objectType, objectId,
                        requestedTopK, requestedMinScore, semanticRawResults, semanticResults);
                return semanticResults;
            }
        }
        recordDiagnostics(RagRetrievalDiagnostics.Strategy.NONE,
                initialResultCount, 0, searchRequest, objectType, objectId,
                requestedTopK, requestedMinScore, lastRawResults, lastResults);
        return List.of();
    }

    private List<VectorSearchResult> limitResults(List<VectorSearchResult> results, int topK) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .limit(Math.max(topK, 0))
                .toList();
    }

    private RagSearchRequest withDefaults(RagSearchRequest request) {
        if (request.minScore() != null) {
            return request;
        }
        return new RagSearchRequest(
                request.query(),
                request.topK(),
                request.metadataFilter(),
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                options.minScore(),
                request.requestedTopK(),
                request.requestedMinScore());
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

    private List<VectorSearchResult> applyMinScore(List<VectorSearchResult> results, Double minScore) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        double effectiveMinScore = minScore == null ? options.minScore() : minScore;
        return results.stream()
                .filter(result -> result.score() >= effectiveMinScore)
                .toList();
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
            Integer requestedTopK,
            Double requestedMinScore,
            List<VectorSearchResult> beforeMinScore,
            List<VectorSearchResult> afterMinScore) {
        if (!diagnosticsOptions.enabled()) {
            return;
        }
        double effectiveMinScore = searchRequest.minScore() == null ? options.minScore() : searchRequest.minScore();
        RagRetrievalDiagnostics diagnostics = new RagRetrievalDiagnostics(
                strategy,
                initialResultCount,
                finalResultCount,
                effectiveMinScore,
                options.vectorWeight(),
                options.lexicalWeight(),
                objectType,
                objectId,
                searchRequest.topK(),
                requestedTopK,
                requestedMinScore,
                safeSize(beforeMinScore),
                safeSize(afterMinScore));
        latestDiagnostics.set(diagnostics);
        logDiagnostics(diagnostics, afterMinScore);
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
