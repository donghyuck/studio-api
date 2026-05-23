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
import studio.one.platform.skillgraph.application.result.SkillClusterView;
import studio.one.platform.skillgraph.application.result.SkillProjectionPointView;
import studio.one.platform.skillgraph.application.result.SkillProjectionSummaryView;
import studio.one.platform.skillgraph.application.result.SkillClusterRepresentativeView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.web.dto.request.SkillProjectionRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillProjectionResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.visualization-base-path:/api/mgmt/skillgraph/visualization}")
@RequiredArgsConstructor
@Validated
public class SkillVisualizationMgmtController {

    private final SkillVisualizationService visualizationService;
    private final SkillCategoryDraftService categoryDraftService;

    @PostMapping("/projections")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillProjectionResponse>> generate(@Valid @RequestBody SkillProjectionRequest request) {
        int limit = request.limit() == null ? 0 : request.limit();
        return ResponseEntity.ok(ApiResponse.ok(SkillProjectionResponse.from(
                visualizationService.generateProjection(request.projectionId(), limit))));
    }

    @GetMapping("/projections")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillProjectionSummaryView>>> projections(
            @PageableDefault(size = 15, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(visualizationService.listProjections(pageable)));
    }

    @GetMapping("/projections/{projectionId}/points")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillProjectionPointView>>> points(
            @PathVariable @Size(max = 100) String projectionId,
            @RequestParam(value = "clusterId", required = false) @Size(max = 100) String clusterId,
            @PageableDefault(size = 500, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualizationService.findProjectionPoints(projectionId, clusterId, pageable)));
    }

    @GetMapping("/projections/{projectionId}/clusters")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillClusterView>>> clusters(
            @PathVariable @Size(max = 100) String projectionId) {
        return ResponseEntity.ok(ApiResponse.ok(visualizationService.findClusters(projectionId)));
    }

    @GetMapping("/projections/{projectionId}/clusters/{clusterId}/representatives")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillClusterRepresentativeView>>> representatives(
            @PathVariable @Size(max = 100) String projectionId,
            @PathVariable @Size(max = 100) String clusterId,
            @RequestParam(value = "includeNoise", required = false, defaultValue = "false") boolean includeNoise,
            @PageableDefault(size = 10, sort = "representativeScore",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(categoryDraftService.findRepresentatives(
                projectionId,
                clusterId,
                includeNoise,
                pageable)));
    }
}
