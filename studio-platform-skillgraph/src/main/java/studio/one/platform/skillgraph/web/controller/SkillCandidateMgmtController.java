package studio.one.platform.skillgraph.web.controller;

import java.util.List;
import java.util.Optional;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCandidateAutoApproveCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateBulkReviewCommand;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.result.SkillCandidateAutoApproveResult;
import studio.one.platform.skillgraph.application.result.SkillCandidateView;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
import studio.one.platform.skillgraph.web.dto.request.SkillCandidateAutoApproveRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillCandidateBulkReviewRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillCandidateReviewRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillCandidateDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.candidate-base-path:/api/mgmt/skillgraph/candidates}")
@RequiredArgsConstructor
@Validated
public class SkillCandidateMgmtController {

    private final SkillCandidateReviewService reviewService;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read') "
            + "and (#sourceType == null or #sourceType.trim().isEmpty() or #sourceId == null or #sourceId.trim().isEmpty() "
            + "or @endpointAuthz.can('objects:' + #sourceType.trim() + ':' + #sourceId.trim(),'read') "
            + "or @endpointAuthz.can('objects:' + #sourceType.trim(),'read'))")
    public ResponseEntity<ApiResponse<Page<SkillCandidateView>>> search(
            @RequestParam(value = "status", required = false) SkillCandidateStatus status,
            @RequestParam(value = "q", required = false) Optional<@Size(max = 200) String> q,
            @RequestParam(value = "sourceType", required = false) @Size(max = 100) String sourceType,
            @RequestParam(value = "sourceId", required = false) @Size(max = 200) String sourceId,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SkillCandidateView> page = reviewService.search(
                status,
                q.map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElse(null),
                sourceType,
                sourceId,
                pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillCandidateDto>> get(@PathVariable @Size(max = 100) String candidateId) {
        return ResponseEntity.ok(ApiResponse.ok(SkillCandidateDto.from(reviewService.get(candidateId))));
    }

    @PatchMapping("/reviews")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<List<SkillCandidateDto>>> reviewAll(
            @Valid @RequestBody SkillCandidateBulkReviewRequest request) {
        List<SkillCandidateDto> reviewed = reviewService.reviewAll(new SkillCandidateBulkReviewCommand(
                request.candidateIds(),
                request.status(),
                request.generateEmbedding(),
                request.reviewerNote())).stream()
                .map(SkillCandidateDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(reviewed));
    }

    @PatchMapping("/auto-approve")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCandidateAutoApproveResult>> autoApprove(
            @Valid @RequestBody SkillCandidateAutoApproveRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.autoApprove(new SkillCandidateAutoApproveCommand(
                request.candidateIds(),
                request.minConfidence(),
                request.minSimilarityScore(),
                request.generateEmbedding(),
                request.reviewerNote()))));
    }

    @PatchMapping("/{candidateId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCandidateDto>> review(
            @PathVariable @Size(max = 100) String candidateId,
            @Valid @RequestBody SkillCandidateReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(SkillCandidateDto.from(reviewService.review(candidateId,
                new SkillCandidateReviewCommand(request.status(), request.matchedSkillId(), request.reviewerNote())))));
    }
}
