package studio.one.platform.skillgraph.web.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.SkillGraphBatchJobView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphBatchJobService;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobStatus;
import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJobType;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("${studio.features.skillgraph.web.base-path:/api/mgmt/skillgraph}")
public class SkillGraphBatchJobMgmtController {

    private final SkillGraphBatchJobService service;

    @GetMapping("/jobs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillGraphBatchJobView>>> jobs(
            @RequestParam(value = "jobType", required = false) SkillGraphBatchJobType jobType,
            @RequestParam(value = "status", required = false) SkillGraphBatchJobStatus status,
            @PageableDefault(size = 15, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(jobType, status, pageable)));
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillGraphBatchJobView>> job(@PathVariable @Size(max = 120) String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(jobId)));
    }
}
