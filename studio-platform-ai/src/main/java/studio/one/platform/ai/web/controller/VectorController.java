package studio.one.platform.ai.web.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.validation.Valid;

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

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.web.dto.VectorDocumentDto;
import studio.one.platform.ai.web.dto.VectorSearchRequestDto;
import studio.one.platform.ai.web.dto.VectorSearchResultDto;
import studio.one.platform.ai.web.dto.VectorUpsertRequestDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Vector store management endpoints under {@code ${studio.ai.endpoints.base-path:/api/ai}/vectors}.
 * Provides vector upsert and similarity search backed by {@link VectorStorePort}, generating
 * embeddings via {@link EmbeddingPort} when raw text queries are supplied.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/vectors")
@Validated
public class VectorController {

    private final EmbeddingPort embeddingPort;
    @Nullable
    private final VectorStorePort vectorStorePort;

    public VectorController(EmbeddingPort embeddingPort,
            @Nullable VectorStorePort vectorStorePort) {
        this.embeddingPort = Objects.requireNonNull(embeddingPort, "embeddingPort");
        this.vectorStorePort = vectorStorePort;
    }

    /**
     * Upserts documents with precomputed embeddings.
     * <pre>
     * POST /api/ai/vectors
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_vector read)
     * {
     *   "documents": [
     *     {"id":"doc-1","content":"raw text","metadata":{"source":"app"},"embedding":[0.1,0.2]}
     *   ]
     * }
     *
     * 200 OK with an empty {@link ApiResponse#ok(Object) data} field.
     * </pre>
     * Throws 503 if no {@link VectorStorePort} is configured.
     */
    @PostMapping
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<Void>> upsert(@Valid @RequestBody VectorUpsertRequestDto request) {
        VectorStorePort store = requireVectorStore();
        List<VectorDocument> documents = request.documents().stream()
                .map(this::toVectorDocument)
                .toList();
        store.upsert(documents);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Accepts either a raw query or an embedding and performs similarity search.
     * <pre>
     * POST /api/ai/vectors/search
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
     * If {@code embedding} is omitted, the controller generates one via {@link EmbeddingPort}.
     */
    @PostMapping("/search")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<List<VectorSearchResultDto>>> search(
            @Valid @RequestBody VectorSearchRequestDto request) {
        VectorStorePort store = requireVectorStore();
        List<Double> queryEmbedding = resolveEmbedding(request);
        VectorSearchRequest searchRequest = new VectorSearchRequest(queryEmbedding, request.topK());
        List<VectorSearchResult> results = store.search(searchRequest);
        List<VectorSearchResultDto> payload = results.stream()
                .map(this::toVectorSearchResultDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    private VectorDocument toVectorDocument(VectorDocumentDto dto) {
        Map<String, Object> metadata = dto.metadata() == null
                ? Collections.emptyMap()
                : Map.copyOf(dto.metadata());
        return new VectorDocument(dto.id(), dto.content(), metadata, List.copyOf(dto.embedding()));
    }

    private List<Double> resolveEmbedding(VectorSearchRequestDto request) {
        if (request.embedding() != null && !request.embedding().isEmpty()) {
            return List.copyOf(request.embedding());
        }
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("Either query text or embedding values must be provided");
        }
        EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(List.of(request.query())));
        return List.copyOf(response.vectors().get(0).values());
    }

    private VectorSearchResultDto toVectorSearchResultDto(VectorSearchResult result) {
        VectorDocument document = result.document();
        Map<String, Object> metadata = document.metadata();
        return new VectorSearchResultDto(
                document.id(),
                document.content(),
                metadata == null ? Collections.emptyMap() : Map.copyOf(metadata),
                result.score());
    }

    private VectorStorePort requireVectorStore() {
        if (vectorStorePort == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vector store is not configured");
        }
        return vectorStorePort;
    }
}
