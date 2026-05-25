package studio.one.platform.skillgraph.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillReferenceEmbeddingCommand;
import studio.one.platform.skillgraph.application.command.SkillReferenceVectorSearchCommand;
import studio.one.platform.skillgraph.application.result.SkillReferenceEmbeddingResult;
import studio.one.platform.skillgraph.application.result.SkillReferenceConceptView;
import studio.one.platform.skillgraph.application.result.SkillReferenceDatasetView;
import studio.one.platform.skillgraph.application.result.SkillReferenceRoadmapContextView;
import studio.one.platform.skillgraph.application.result.SkillReferenceVectorSearchResult;
import studio.one.platform.skillgraph.application.usecase.SkillReferenceDatasetService;
import studio.one.platform.skillgraph.web.dto.request.SkillReferenceEmbeddingRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillReferenceSearchRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillReferenceVectorSearchRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("${studio.features.skillgraph.web.reference-datasets-base-path:/api/mgmt/skillgraph/datasets}")
public class SkillReferenceDatasetMgmtController {

    private final SkillReferenceDatasetService service;

    @GetMapping("/dataset-ids")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillReferenceDatasetView>>> datasetIds(
            @PageableDefault(size = 15, sort = "datasetId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listDatasets(pageable)));
    }

    @GetMapping("/{datasetId}/concepts")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillReferenceConceptView>>> concepts(
            @PathVariable @Size(max = 80) String datasetId,
            @RequestParam(required = false) @Size(max = 80) String conceptType,
            @RequestParam(required = false, name = "q") @Size(max = 200) String query,
            @PageableDefault(size = 15, sort = "conceptId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listConcepts(datasetId, conceptType, query, pageable)));
    }

    @GetMapping("/{datasetId}/concepts/{conceptId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillReferenceConceptView>> concept(
            @PathVariable @Size(max = 80) String datasetId,
            @PathVariable @Size(max = 120) String conceptId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getConcept(datasetId, conceptId)));
    }

    @GetMapping("/{datasetId}/concepts/{conceptId}/children")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillReferenceConceptView>>> children(
            @PathVariable @Size(max = 80) String datasetId,
            @PathVariable @Size(max = 120) String conceptId,
            @RequestParam(required = false) @Size(max = 80) String relationType,
            @PageableDefault(size = 15, sort = "conceptId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.listChildren(datasetId, conceptId, relationType, pageable)));
    }

    @PostMapping("/reference-search")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillReferenceConceptView>>> search(
            @Valid @RequestBody SkillReferenceSearchRequest request,
            @PageableDefault(size = 15, sort = "conceptId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(
                request.datasetId(),
                request.conceptType(),
                request.query(),
                pageable)));
    }

    @PostMapping("/embeddings/vectorize")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillReferenceEmbeddingResult>> vectorize(
            @Valid @RequestBody SkillReferenceEmbeddingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.embedConcepts(new SkillReferenceEmbeddingCommand(
                request.datasetId(),
                request.provider(),
                request.conceptType(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDim(),
                request.textType(),
                request.textBuildStrategy(),
                request.batchSize(),
                request.overwrite(),
                request.normalize()))));
    }

    @PostMapping("/embeddings/vector-search")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillReferenceVectorSearchResult>>> vectorSearch(
            @Valid @RequestBody SkillReferenceVectorSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.vectorSearch(new SkillReferenceVectorSearchCommand(
                request.datasetId(),
                request.provider(),
                request.conceptType(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.textType(),
                request.query(),
                request.topK(),
                request.minScore(),
                request.categoryPathPrefix(),
                request.levelValue(),
                request.normalize()))));
    }

    @GetMapping("/{datasetId}/competency-units/{conceptId}/roadmap-context")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillReferenceRoadmapContextView>> roadmapContext(
            @PathVariable @Size(max = 80) String datasetId,
            @PathVariable @Size(max = 120) String conceptId) {
        return ResponseEntity.ok(ApiResponse.ok(service.roadmapContext(datasetId, conceptId)));
    }
}
