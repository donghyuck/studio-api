package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.CourseRecommendationView;
import studio.one.platform.skillgraph.application.usecase.SkillRecommendationService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.recommendation-base-path:/api/mgmt/skillgraph/recommendations}")
@RequiredArgsConstructor
@Validated
public class SkillRecommendationMgmtController {
    private final SkillRecommendationService service;

    @PostMapping("/courses")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<CourseRecommendationView>>> courses(@Valid @RequestBody CourseRecommendationRequest request) {
        int limit = request.limit() == null ? 10 : request.limit();
        return ResponseEntity.ok(ApiResponse.ok(service.recommendCourses(request.targetSkillIds(), request.ownedSkillIds(), limit)));
    }

    public record CourseRecommendationRequest(
            List<String> targetSkillIds,
            List<String> ownedSkillIds,
            @Min(1) @Max(100) Integer limit) {
    }
}
