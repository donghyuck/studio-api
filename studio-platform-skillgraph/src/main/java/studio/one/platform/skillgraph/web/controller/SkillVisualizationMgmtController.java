package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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
import studio.one.platform.skillgraph.application.usecase.SkillVisualizationService;
import studio.one.platform.skillgraph.web.dto.request.SkillProjectionRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillClusterDto;
import studio.one.platform.skillgraph.web.dto.response.SkillProjectionPointDto;
import studio.one.platform.skillgraph.web.dto.response.SkillProjectionResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.visualization-base-path:/api/mgmt/skillgraph/visualization}")
@RequiredArgsConstructor
@Validated
public class SkillVisualizationMgmtController {

    private final SkillVisualizationService visualizationService;

    @PostMapping("/projections")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillProjectionResponse>> generate(@Valid @RequestBody SkillProjectionRequest request) {
        int limit = request.limit() == null ? 1000 : request.limit();
        return ResponseEntity.ok(ApiResponse.ok(SkillProjectionResponse.from(
                visualizationService.generateProjection(request.projectionId(), limit))));
    }

    @GetMapping("/projections/{projectionId}/points")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillProjectionPointDto>>> points(
            @PathVariable @Size(max = 100) String projectionId,
            @RequestParam(value = "clusterId", required = false) @Size(max = 100) String clusterId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") @Min(1) @Max(500) int limit,
            @RequestParam(value = "offset", required = false, defaultValue = "0") @Min(0) int offset) {
        return ResponseEntity.ok(ApiResponse.ok(visualizationService
                .findProjectionPoints(projectionId, clusterId, limit, offset).stream()
                .map(SkillProjectionPointDto::from)
                .toList()));
    }

    @GetMapping("/projections/{projectionId}/clusters")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillClusterDto>>> clusters(@PathVariable @Size(max = 100) String projectionId) {
        return ResponseEntity.ok(ApiResponse.ok(visualizationService.findClusters(projectionId).stream()
                .map(SkillClusterDto::from)
                .toList()));
    }
}
