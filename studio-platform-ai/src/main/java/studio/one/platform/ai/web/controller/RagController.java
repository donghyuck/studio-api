package studio.one.platform.ai.web.controller;

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

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Validated
public class RagController {

    private final RagPipelineService ragPipelineService;

    public RagController(RagPipelineService ragPipelineService) {
        this.ragPipelineService = ragPipelineService;
    }

    @PostMapping("/index")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<Void> index(@Valid @RequestBody IndexRequest request) {
        Map<String, Object> metadata = request.metadata() == null ? Map.of() : request.metadata();
        ragPipelineService.index(new RagIndexRequest(request.documentId(), request.text(), metadata));
        return ResponseEntity.accepted().build();
    }

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
                .collect(Collectors.toList());
        return ResponseEntity.ok(new SearchResponse(payload));
    }
}
