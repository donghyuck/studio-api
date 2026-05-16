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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillCandidateReviewCommand;
import studio.one.platform.skillgraph.application.usecase.SkillCandidateReviewService;
import studio.one.platform.skillgraph.domain.model.SkillCandidateStatus;
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
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillCandidateDto>>> search(
            @RequestParam(value = "status", required = false) SkillCandidateStatus status,
            @RequestParam(value = "q", required = false) @Size(max = 200) String q,
            @RequestParam(value = "limit", required = false, defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.search(status, q, limit).stream()
                .map(SkillCandidateDto::from)
                .toList()));
    }

    @GetMapping("/{candidateId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillCandidateDto>> get(@PathVariable @Size(max = 100) String candidateId) {
        return ResponseEntity.ok(ApiResponse.ok(SkillCandidateDto.from(reviewService.get(candidateId))));
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
