package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingInputType;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.service.pipeline.RagEmbeddingProfileResolver;
import studio.one.platform.ai.service.pipeline.RagEmbeddingSelection;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.ResolvedRagEmbedding;
import studio.one.platform.ai.service.pipeline.AiProviderExceptionSupport;
import studio.one.platform.ai.web.dto.VectorDocumentDto;
import studio.one.platform.ai.web.dto.VectorSearchRequestDto;
import studio.one.platform.ai.web.dto.VectorSearchResultDto;
import studio.one.platform.ai.web.dto.VectorUpsertRequestDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * 벡터 업서트와 검색을 제공합니다. 벡터 스토어가 없으면 503을 발생시켜 구성 여부를 명확히 합니다.
 * 
 * Vector store management endpoints under
 * {@code ${studio.ai.endpoints.mgmt-base-path:/api/mgmt/ai}/vectors}.
 * Provides vector upsert and similarity search backed by
 * {@link VectorStorePort}, generating
 * embeddings via {@link EmbeddingPort} when raw text queries are supplied.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/vectors")
@Validated
@Slf4j
public class VectorController {

    private static final double HYBRID_VECTOR_WEIGHT = 0.7;
    private static final double HYBRID_LEXICAL_WEIGHT = 0.3;

    private final EmbeddingPort embeddingPort;
    @Nullable
    private final RagEmbeddingProfileResolver embeddingProfileResolver;
    @Nullable
    private final VectorStorePort vectorStorePort; 
    private final RagPipelineOptions searchOptions;

    public VectorController(EmbeddingPort embeddingPort,
            @Nullable VectorStorePort vectorStorePort) {
        this(embeddingPort, null, vectorStorePort);
    }

    public VectorController(EmbeddingPort embeddingPort,
            @Nullable RagEmbeddingProfileResolver embeddingProfileResolver,
            @Nullable VectorStorePort vectorStorePort) {
        this(embeddingPort, embeddingProfileResolver, vectorStorePort, RagPipelineOptions.defaults());
    }

    public VectorController(EmbeddingPort embeddingPort,
            @Nullable RagEmbeddingProfileResolver embeddingProfileResolver,
            @Nullable VectorStorePort vectorStorePort,
            RagPipelineOptions searchOptions) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.embeddingProfileResolver = embeddingProfileResolver;
        this.vectorStorePort = vectorStorePort; 
        this.searchOptions = searchOptions == null ? RagPipelineOptions.defaults() : searchOptions;
    }

    /**
     * Upserts documents with precomputed embeddings.
     * 
     * <pre>
     * POST /api/mgmt/ai/vectors
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_vector read)
     * {
     *   "documents": [
     *     {"id":"doc-1","content":"raw text","metadata":{"source":"app"},"embedding":[0.1,0.2]}
     *   ]
     * }
     *
     * 200 OK with an empty {@link ApiResponse#ok(Object) data} field.
     * </pre>
     * 
     * Throws 503 if no {@link VectorStorePort} is configured.
     */
    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<Void>> upsert(@Valid @RequestBody VectorUpsertRequestDto request) {
        VectorStorePort store = requireVectorStore();
        if (request.documents() == null || request.documents().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents must not be empty");
        }
        int expectedDim = request.documents().get(0).embedding().size();
        if (expectedDim <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embedding size must be positive");
        }
        List<VectorDocument> documents = new ArrayList<>(request.documents().size());
        for (VectorDocumentDto dto : request.documents()) {
            List<Double> embedding = normalizeEmbedding(dto.embedding(), expectedDim);
            documents.add(toVectorDocument(dto, embedding));
        }
        store.upsert(documents);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Accepts either a raw query or an embedding and performs similarity search.
     * 
     * <pre>
     * POST /api/mgmt/ai/vectors/search
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_vector read)
     * {
     *   "query": "search text",
     *   "topK": 3
     * }
     *
     * 200 OK
     * {
     *   "data": [
     *     {"id":"doc-1","content":"...","metadata":{},"score":0.91}
     *   ]
     * }
     * </pre>
     * 
     * Options:
     * - If {@code embedding} is omitted, the controller generates one via {@link EmbeddingPort}.
     * - Set {@code hybrid=true} to use BM25+vector hybrid search (requires {@code query} text).
     */
    @PostMapping("/search")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> search(
            @Valid @RequestBody VectorSearchRequestDto request) {

        VectorStorePort store = requireVectorStore();
        validateObjectScope(request.objectType(), request.objectId(), "Vector object scope");
        ResolvedVectorEmbedding resolvedEmbedding = resolveEmbedding(request);
        VectorSearchRequest searchRequest = new VectorSearchRequest(
                resolvedEmbedding.values(),
                request.query(),
                effectiveTopK(request.topK()),
                metadataFilter(request, resolvedEmbedding),
                effectiveMinScore(request.minScore()),
                request.includeText(),
                request.includeMetadata());
        VectorSearchResults results = executeSearch(store, request, searchRequest);
        List<VectorSearchResultDto> payload = results.hits().stream()
                .map(this::toVectorSearchResultDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    private VectorDocument toVectorDocument(VectorDocumentDto dto, List<Double> embedding) {
        Map<String, Object> metadata = dto.metadata() == null
                ? new HashMap<>()
                : new HashMap<>(dto.metadata());
        metadata.putIfAbsent("objectType", "api"); // pgvector schema requires object_type
        metadata.putIfAbsent("chunkOrder", 0); // chunk_index 기본값
        return new VectorDocument(dto.id(), dto.content(), metadata, embedding);
    }

    private MetadataFilter metadataFilter(VectorSearchRequestDto request, ResolvedVectorEmbedding resolvedEmbedding) {
        MetadataFilter objectScope = MetadataFilter.objectScope(request.objectType(), request.objectId());
        Map<String, Object> equals = new HashMap<>(objectScope.equalsCriteria());
        if (hasEmbeddingSelection(request)) {
            putIfPresent(equals, "embeddingProfileId", resolvedEmbedding.profileId());
            putIfPresent(equals, "embeddingProvider", resolvedEmbedding.provider());
            putIfPresent(equals, "embeddingModel", resolvedEmbedding.model());
            putIfPresent(equals, "embeddingDimension", resolvedEmbedding.dimension());
        }
        return MetadataFilter.of(equals, objectScope.inCriteria(), objectScope.rangeCriteria());
    }

    private void validateObjectScope(String objectType, String objectId, String label) {
        String normalizedObjectType = normalizeText(objectType);
        String normalizedObjectId = normalizeText(objectId);
        if ((normalizedObjectType == null) != (normalizedObjectId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " requires both objectType and objectId");
        }
    }

    private int effectiveTopK(Integer requestedTopK) {
        return requestedTopK == null ? searchOptions.topK() : requestedTopK;
    }

    private Double effectiveMinScore(Double requestedMinScore) {
        return requestedMinScore == null ? searchOptions.minScore() : requestedMinScore;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            values.put(key, value instanceof String text ? text.trim() : value);
        }
    }

    private ResolvedVectorEmbedding resolveEmbedding(VectorSearchRequestDto request) {
        if (request.embedding() != null && !request.embedding().isEmpty()) {
            ResolvedRagEmbedding resolved = resolveSelection(request);
            return new ResolvedVectorEmbedding(
                    List.copyOf(request.embedding()),
                    resolved == null ? request.embeddingProfileId() : resolved.profileId(),
                    resolved == null ? request.embeddingProvider() : resolved.provider(),
                    resolved == null ? request.embeddingModel() : resolved.model(),
                    resolved == null
                            ? request.embedding().size()
                            : resolved.dimension() == null ? request.embedding().size() : resolved.dimension());
        }
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Either query text or embedding values must be provided");
        }
        EmbeddingResponse response;
        ResolvedRagEmbedding resolved = null;
        if (embeddingProfileResolver == null || !hasEmbeddingSelection(request)) {
            response = embedForSearch(embeddingPort, new EmbeddingRequest(List.of(request.query())));
        } else {
            resolved = resolveSelection(request);
            response = embedForSearch(resolved.embeddingPort(), resolved.request(List.of(request.query())));
        }
        log.debug("embedding {} -> {}", request.query(), response.vectors().size());
        List<Double> values = List.copyOf(response.vectors().get(0).values());
        return new ResolvedVectorEmbedding(
                values,
                resolved == null ? null : resolved.profileId(),
                resolved == null ? null : resolved.provider(),
                resolved == null ? null : resolved.model(),
                resolved == null
                        ? null
                        : resolved.dimension() == null ? values.size() : resolved.dimension());
    }

    private EmbeddingResponse embedForSearch(EmbeddingPort port, EmbeddingRequest request) {
        try {
            return port.embed(request);
        } catch (RuntimeException ex) {
            if (AiProviderExceptionSupport.isQuotaOrRateLimit(ex)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Embedding provider quota exceeded while converting vector search query to an embedding. "
                                + "Query-based vector search calls the configured EmbeddingPort; provide a "
                                + "precomputed embedding or use RAG chunk inspection endpoints for provider-free "
                                + "inspection.",
                        ex);
            }
            throw ex;
        }
    }

    private ResolvedRagEmbedding resolveSelection(VectorSearchRequestDto request) {
        if (embeddingProfileResolver == null || !hasEmbeddingSelection(request)) {
            return null;
        }
        return embeddingProfileResolver.resolve(new RagEmbeddingSelection(
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                EmbeddingInputType.TEXT));
    }

    private boolean hasEmbeddingSelection(VectorSearchRequestDto request) {
        return request.embeddingProfileId() != null
                || request.embeddingProvider() != null
                || request.embeddingModel() != null;
    }

    private VectorSearchResults executeSearch(VectorStorePort store, VectorSearchRequestDto request,
            VectorSearchRequest searchRequest) {
        boolean useHybrid = Boolean.TRUE.equals(request.hybrid());
        MetadataFilter filter = searchRequest.metadataFilter();
        boolean hasObjectFilter = filter.hasObjectScope();
        if (!useHybrid) {
            if (hasObjectFilter) {
                return toSearchResults(
                        store.searchByObject(filter.objectType(), filter.objectId(), searchRequest),
                        searchRequest);
            }
            return applyMinScore(store.searchWithFilter(searchRequest), searchRequest.minScore());
        }
        if (request.query() == null || request.query().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hybrid search requires a query string");
        }
        List<VectorSearchResult> results = hasObjectFilter
                ? store.hybridSearchByObject(
                        request.query(),
                        filter.objectType(),
                        filter.objectId(),
                        searchRequest,
                        HYBRID_VECTOR_WEIGHT,
                        HYBRID_LEXICAL_WEIGHT)
                : store.hybridSearch(request.query(), searchRequest, HYBRID_VECTOR_WEIGHT, HYBRID_LEXICAL_WEIGHT);
        return toSearchResults(results, searchRequest);
    }

    private List<Double> normalizeEmbedding(List<Double> embedding, int expectedDim) {
        if (embedding == null || embedding.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embedding cannot be empty");
        }
        if (embedding.size() != expectedDim) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "embedding dimension mismatch: expected " + expectedDim + " but got " + embedding.size());
        }
        double normSq = 0.0d;
        for (Double v : embedding) {
            double val = v == null ? 0.0d : v;
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        if (norm == 0.0d) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embedding norm must be > 0");
        }
        List<Double> normalized = new ArrayList<>(embedding.size());
        for (Double v : embedding) {
            normalized.add((v == null ? 0.0d : v) / norm);
        }
        return normalized;
    }

    private VectorSearchResultDto toVectorSearchResultDto(VectorSearchHit hit) {
        return new VectorSearchResultDto(
                hit.id(),
                hit.documentId(),
                hit.text(),
                hit.metadata(),
                hit.score());
    }

    private VectorSearchResults toSearchResults(List<VectorSearchResult> results, VectorSearchRequest request) {
        long startedAt = System.nanoTime();
        List<VectorSearchHit> hits = results.stream()
                .map(result -> VectorSearchHit.from(result, request.includeText(), request.includeMetadata()))
                .toList();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return applyMinScore(VectorSearchResults.of(hits, elapsedMs), request.minScore());
    }

    private VectorSearchResults applyMinScore(VectorSearchResults results, Double minScore) {
        if (minScore == null) {
            return results;
        }
        List<VectorSearchHit> hits = results.hits().stream()
                .filter(result -> result.score() >= minScore)
                .toList();
        return VectorSearchResults.of(hits, results.elapsedMs());
    }

    private VectorStorePort requireVectorStore() {
        if (vectorStorePort == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vector store is not configured");
        }
        return vectorStorePort;
    }

    private record ResolvedVectorEmbedding(
            List<Double> values,
            String profileId,
            String provider,
            String model,
            Integer dimension) {
    }
}
