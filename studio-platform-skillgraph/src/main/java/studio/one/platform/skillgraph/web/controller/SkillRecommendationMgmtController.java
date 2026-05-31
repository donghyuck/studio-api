package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCandidateRecommendationJobCommand;
import studio.one.platform.skillgraph.application.command.SkillRecommendationApplyCommand;
import studio.one.platform.skillgraph.application.result.SkillRecommendationApplyResult;
import studio.one.platform.skillgraph.application.result.SkillRecommendationJobView;
import studio.one.platform.skillgraph.application.result.SkillRecommendationResultView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateRecommendationService;
import studio.one.platform.skillgraph.web.dto.request.SkillCandidateRecommendationJobRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillRecommendationApplyRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("${studio.features.skillgraph.web.recommendations-base-path:/api/mgmt/skillgraph}")
public class SkillRecommendationMgmtController {

    private final SkillCandidateRecommendationService service;

    /**
     * Create a new skill recommendation job for the given candidates and parameters.
     * @param request
     * @return
     */
    @PostMapping("/candidates/recommendation-jobs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillRecommendationJobView>> createJob(
            @Valid @RequestBody SkillCandidateRecommendationJobRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.createJob(new SkillCandidateRecommendationJobCommand(
                request.targetScope(),
                request.candidateIds(),
                request.status(),
                request.keyword(),
                request.sourceType(),
                request.sourceId(),
                request.embeddingProvider(),
                request.embeddingModel(),
                request.embeddingDimension(),
                request.targetTypes(),
                request.topK(),
                request.minScore(),
                request.newSkillMinConfidence(),
                request.existingSkillMinScore()))));
    }

    @GetMapping("/recommendations/jobs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillRecommendationJobView>>> jobs(
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.searchJobs(pageable)));
    }

    @GetMapping("/recommendations/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillRecommendationJobView>> job(@PathVariable @Size(max = 120) String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getJob(jobId)));
    }

    @GetMapping("/recommendations/jobs/{jobId}/results")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillRecommendationResultView>>> jobResults(
            @PathVariable @Size(max = 120) String jobId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.getJobResults(jobId, pageable)));
    }

    @GetMapping("/candidates/{candidateId}/recommendations")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillRecommendationResultView>>> candidateResults(
            @PathVariable @Size(max = 100) String candidateId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getCandidateResults(candidateId)));
    }

    @PostMapping("/recommendations/results/{resultId}/apply")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillRecommendationApplyResult>> applyResult(
            @PathVariable @Size(max = 120) String resultId,
            @Valid @RequestBody SkillRecommendationApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.applyResult(resultId, applyCommand(request))));
    }

    @PostMapping("/recommendations/jobs/{jobId}/apply")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillRecommendationApplyResult>> applyJob(
            @PathVariable @Size(max = 120) String jobId,
            @Valid @RequestBody SkillRecommendationApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.applyJob(jobId, applyCommand(request))));
    }

    private SkillRecommendationApplyCommand applyCommand(SkillRecommendationApplyRequest request) {
        return new SkillRecommendationApplyCommand(
                request.applyMode(),
                request.recommendationTypes(),
                request.minConfidence(),
                request.minSimilarityScore());
    }
}
