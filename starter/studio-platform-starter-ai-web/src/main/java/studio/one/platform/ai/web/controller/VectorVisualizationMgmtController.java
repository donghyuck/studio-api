package studio.one.platform.ai.web.controller;

import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.VectorVisualizationMetadataSanitizer;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.service.visualization.VectorProjectionCreateCommand;
import studio.one.platform.ai.service.visualization.VectorProjectionService;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationCommand;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationResult;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationService;
import studio.one.platform.ai.web.dto.visualization.ProjectionCreateRequest;
import studio.one.platform.ai.web.dto.visualization.ProjectionCreateResponse;
import studio.one.platform.ai.web.dto.visualization.ProjectionDetailResponse;
import studio.one.platform.ai.web.dto.visualization.ProjectionListResponse;
import studio.one.platform.ai.web.dto.visualization.ProjectionPointResponse;
import studio.one.platform.ai.web.dto.visualization.ProjectionPointsResponse;
import studio.one.platform.ai.web.dto.visualization.ProjectionSummaryResponse;
import studio.one.platform.ai.web.dto.visualization.VectorItemDetailResponse;
import studio.one.platform.ai.web.dto.visualization.VectorSearchVisualizationRequest;
import studio.one.platform.ai.web.dto.visualization.VectorSearchVisualizationResponse;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/vectors")
@Validated
public class VectorVisualizationMgmtController {

    private final VectorProjectionService projectionService;
    @Nullable
    private final VectorSearchVisualizationService searchVisualizationService;

    public VectorVisualizationMgmtController(
            VectorProjectionService projectionService,
            @Nullable VectorSearchVisualizationService searchVisualizationService) {
        this.projectionService = projectionService;
        this.searchVisualizationService = searchVisualizationService;
    }

    @PostMapping("/projections")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','admin')")
    public ResponseEntity<ApiResponse<ProjectionCreateResponse>> createProjection(
            @Valid @RequestBody ProjectionCreateRequest request) {
        VectorProjection projection = projectionService.create(new VectorProjectionCreateCommand(
                request.name(),
                algorithm(request.algorithm()),
                request.targetTypes(),
                request.filters(),
                null));
        return ResponseEntity.ok(ApiResponse.ok(new ProjectionCreateResponse(
                projection.projectionId(),
                projection.status().name(),
                "벡터 시각화 좌표 생성 작업이 요청되었습니다.")));
    }

    @GetMapping("/projections")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<ProjectionListResponse>> listProjections(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(ApiResponse.ok(new ProjectionListResponse(
                projectionService.list(limit, offset).stream()
                        .map(this::summary)
                        .toList())));
    }

    @GetMapping("/projections/{projectionId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','read')")
    public ResponseEntity<ApiResponse<ProjectionDetailResponse>> projection(
            @PathVariable String projectionId) {
        return ResponseEntity.ok(ApiResponse.ok(detail(projectionService.get(projectionId))));
    }

    @GetMapping("/projections/{projectionId}/points")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','admin')")
    public ResponseEntity<ApiResponse<ProjectionPointsResponse>> points(
            @PathVariable String projectionId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "2000") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        VectorProjection projection = projectionService.get(projectionId);
        ProjectionPointPage page = projectionService.points(
                projectionId,
                targetType,
                clusterId,
                keyword,
                limit,
                offset);
        return ResponseEntity.ok(ApiResponse.ok(new ProjectionPointsResponse(
                projectionId,
                projection.algorithm().name(),
                page.totalCount(),
                page.items().stream().map(this::point).toList())));
    }

    @GetMapping("/items/{vectorItemId}")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','admin')")
    public ResponseEntity<ApiResponse<VectorItemDetailResponse>> item(@PathVariable String vectorItemId) {
        return ResponseEntity.ok(ApiResponse.ok(itemDetail(projectionService.item(vectorItemId))));
    }

    @PostMapping("/search-visualization")
    @PreAuthorize("@endpointAuthz.can('services:ai_vector','admin')")
    public ResponseEntity<ApiResponse<VectorSearchVisualizationResponse>> searchVisualization(
            @Valid @RequestBody VectorSearchVisualizationRequest request) {
        if (searchVisualizationService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vector search visualization is not configured");
        }
        VectorSearchVisualizationResult result = searchVisualizationService.search(new VectorSearchVisualizationCommand(
                request.projectionId(),
                request.query(),
                request.targetTypes(),
                request.topK(),
                request.minScore()));
        return ResponseEntity.ok(ApiResponse.ok(searchResponse(result)));
    }

    private ProjectionAlgorithm algorithm(String value) {
        if (value == null || value.isBlank()) {
            return ProjectionAlgorithm.PCA;
        }
        try {
            return ProjectionAlgorithm.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROJECTION_ALGORITHM", ex);
        }
    }

    private ProjectionSummaryResponse summary(VectorProjection projection) {
        return new ProjectionSummaryResponse(
                projection.projectionId(),
                projection.name(),
                projection.algorithm().name(),
                projection.status().name(),
                projection.targetTypes(),
                projection.itemCount(),
                projection.createdAt(),
                projection.completedAt());
    }

    private ProjectionDetailResponse detail(VectorProjection projection) {
        return new ProjectionDetailResponse(
                projection.projectionId(),
                projection.name(),
                projection.algorithm().name(),
                projection.status().name(),
                projection.targetTypes(),
                projection.filters(),
                projection.itemCount(),
                projection.errorMessage(),
                projection.createdAt(),
                projection.completedAt());
    }

    private ProjectionPointResponse point(ProjectionPointView point) {
        return new ProjectionPointResponse(
                point.vectorItemId(),
                point.targetType(),
                point.sourceId(),
                point.label(),
                point.x(),
                point.y(),
                point.clusterId(),
                point.metadata());
    }

    private VectorItemDetailResponse itemDetail(VectorItem item) {
        return new VectorItemDetailResponse(
                item.vectorItemId(),
                item.targetType(),
                item.sourceId(),
                item.label(),
                item.contentText(),
                item.embeddingModel(),
                item.embeddingDimension(),
                VectorVisualizationMetadataSanitizer.sanitize(item.metadata()),
                item.createdAt());
    }

    private VectorSearchVisualizationResponse searchResponse(VectorSearchVisualizationResult result) {
        return new VectorSearchVisualizationResponse(
                new VectorSearchVisualizationResponse.QueryPoint(
                        result.query().label(),
                        result.query().x(),
                        result.query().y()),
                result.results().stream()
                        .map(point -> new VectorSearchVisualizationResponse.ResultPoint(
                                point.vectorItemId(),
                                point.targetType(),
                                point.sourceId(),
                                point.label(),
                                point.x(),
                                point.y(),
                                point.similarity()))
                        .toList());
    }
}
