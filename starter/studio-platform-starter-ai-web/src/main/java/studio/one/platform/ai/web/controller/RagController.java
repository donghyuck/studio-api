package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Map;

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

import studio.one.platform.ai.core.MetadataFilter;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.core.rag.RagIndexJobCreateRequest;
import studio.one.platform.ai.core.rag.RagIndexJobStatus;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.EmbeddingProviderQuotaExceededException;
import studio.one.platform.ai.service.pipeline.RagPipelineOptions;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.dto.IndexRequest;
import studio.one.platform.ai.web.dto.SearchRequest;
import studio.one.platform.ai.web.dto.SearchResponse;
import studio.one.platform.ai.web.dto.SearchResult;
import studio.one.platform.constant.PropertyKeys;

/**
 * RAG 인덱싱과 검색을 처리합니다. RagPipelineService를 통해 텍스트 인덱싱 및 질의 검색을 수행합니다
 * 
 * RAG pipeline endpoints for indexing and searching documents.
 * <p>Base path: {@code ${studio.ai.endpoints.mgmt-base-path:/api/mgmt/ai}/rag}. Supports:
 * <ul>
 *   <li>{@code POST /index} for registering content with optional metadata.</li>
 *   <li>{@code POST /search} for semantic lookup returning ranked results.</li>
 * </ul>
 */
@RestController 
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag")
@Validated
public class RagController {

    public static final String RAG_JOB_ID_HEADER = "X-RAG-Job-Id";

    private final RagPipelineService ragPipelineService;
    private final RagPipelineOptions options;
    @Nullable
    private final RagIndexJobService ragIndexJobService;

    public RagController(RagPipelineService ragPipelineService) {
        this(ragPipelineService, null, RagPipelineOptions.defaults());
    }

    public RagController(RagPipelineService ragPipelineService,
            @Nullable RagIndexJobService ragIndexJobService) {
        this(ragPipelineService, ragIndexJobService, RagPipelineOptions.defaults());
    }

    public RagController(
            RagPipelineService ragPipelineService,
            @Nullable RagIndexJobService ragIndexJobService,
            RagPipelineOptions options) {
        this.ragPipelineService = ragPipelineService;
        this.options = options == null ? RagPipelineOptions.defaults() : options;
        this.ragIndexJobService = ragIndexJobService;
    }

    /**
     * Indexes a document for later retrieval.
     * <pre>
     * POST /api/mgmt/ai/rag/index
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_rag read)
     * {
     *   "documentId": "doc-123",
     *   "text": "Full text to embed",
     *   "metadata": {"tenant":"acme"},
     *   "keywords": ["optional", "user provided", "keywords"],
     *   "useLlmKeywordExtraction": true
     * }
     *
     * 202 Accepted with empty body.
     * </pre>
     */
    @PostMapping("/index")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<Void> index(@Valid @RequestBody IndexRequest request) {
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        List<String> keywords = request.keywords() == null ? List.of() : request.keywords();
        boolean useLlmKeywords = Boolean.TRUE.equals(request.useLlmKeywordExtraction());
        RagIndexRequest indexRequest = new RagIndexRequest(
                request.documentId(),
                request.text(),
                metadata,
                keywords,
                useLlmKeywords,
                request.embeddingProfileId(),
                request.embeddingProvider(),
                request.embeddingModel());
        if (ragIndexJobService == null) {
            ragPipelineService.index(indexRequest);
            return ResponseEntity.accepted().build();
        }
        RagIndexJob job = ragIndexJobService.createJob(RagIndexJobCreateRequest.forIndexRequest(indexRequest));
        RagIndexJob completed = ragIndexJobService.startJob(job.jobId());
        if (completed.status() == RagIndexJobStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, completed.errorMessage());
        }
        return ResponseEntity.accepted()
                .header(RAG_JOB_ID_HEADER, job.jobId())
                .build();
    }

    /**
     * Performs a semantic search across indexed content.
     * <pre>
     * POST /api/mgmt/ai/rag/search
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_rag read)
     * {
     *   "query": "find me something",
     *   "topK": 5
     * }
     *
     * 200 OK
     * {
     *   "results": [
     *     {"documentId":"doc-123","content":"...", "metadata":{}, "score":0.92}
     *   ]
     * }
     * </pre>
     */
    @PostMapping("/search")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        List<RagSearchResult> results;
        try {
            results = ragPipelineService.search(new RagSearchRequest(
                    request.query(),
                    effectiveTopK(request.topK()),
                    objectScope(request.objectType(), request.objectId()),
                    request.embeddingProfileId(),
                    request.embeddingProvider(),
                    request.embeddingModel(),
                    effectiveMinScore(request.minScore()),
                    request.topK(),
                    request.minScore()));
        } catch (EmbeddingProviderQuotaExceededException ex) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Embedding provider quota exceeded while executing RAG search. Query-based RAG search "
                            + "generates an embedding before vector lookup; use RAG chunk inspection endpoints "
                            + "for provider-free inspection.",
                    ex);
        }
        List<SearchResult> payload = results.stream()
                .map(result -> new SearchResult(
                        result.documentId(),
                        result.content(),
                        result.metadata(),
                        result.score()))
                .toList();
        return ResponseEntity.ok(new SearchResponse(payload));
    }

    private int effectiveTopK(Integer requestedTopK) {
        return requestedTopK == null ? options.topK() : requestedTopK;
    }

    private double effectiveMinScore(Double requestedMinScore) {
        return requestedMinScore == null ? options.minScore() : requestedMinScore;
    }

    private MetadataFilter objectScope(String objectType, String objectId) {
        String normalizedObjectType = normalize(objectType);
        String normalizedObjectId = normalize(objectId);
        if (normalizedObjectType == null && normalizedObjectId == null) {
            return MetadataFilter.empty();
        }
        if (normalizedObjectType == null || normalizedObjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RAG object scope requires both objectType and objectId");
        }
        return MetadataFilter.objectScope(normalizedObjectType, normalizedObjectId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
