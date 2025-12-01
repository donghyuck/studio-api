package studio.one.platform.ai.web.controller;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
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
 * <p>Base path: {@code ${studio.ai.endpoints.base-path:/api/ai}/rag}. Supports:
 * <ul>
 *   <li>{@code POST /index} for registering content with optional metadata.</li>
 *   <li>{@code POST /search} for semantic lookup returning ranked results.</li>
 * </ul>
 */
@RestController 
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/rag")
@Validated
public class RagController {

    private final RagPipelineService ragPipelineService;

    public RagController(RagPipelineService ragPipelineService) {
        this.ragPipelineService = ragPipelineService;
    }

    /**
     * Indexes a document for later retrieval.
     * <pre>
     * POST /api/ai/rag/index
     * Authorization: Bearer &lt;token&gt;   (requires services:ai_rag read)
     * {
     *   "documentId": "doc-123",
     *   "text": "Full text to embed",
     *   "metadata": {"tenant":"acme"}
     * }
     *
     * 202 Accepted with empty body.
     * </pre>
     */
    @PostMapping("/index")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<Void> index(@Valid @RequestBody IndexRequest request) {
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        ragPipelineService.index(new RagIndexRequest(request.documentId(), request.text(), metadata));
        return ResponseEntity.accepted().build();
    }

    /**
     * Performs a semantic search across indexed content.
     * <pre>
     * POST /api/ai/rag/search
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
        List<RagSearchResult> results = ragPipelineService.search(new RagSearchRequest(request.query(), request.topK()));
        List<SearchResult> payload = results.stream()
                .map(result -> new SearchResult(
                        result.documentId(),
                        result.content(),
                        result.metadata(),
                        result.score()))
                .toList();
        return ResponseEntity.ok(new SearchResponse(payload));
    }
}
