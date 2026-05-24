package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

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
import studio.one.platform.skillgraph.application.command.SkillDatasetImportCommand;
import studio.one.platform.skillgraph.application.service.SkillDatasetImportJobService;
import studio.one.platform.skillgraph.web.dto.request.SkillDatasetImportRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillDatasetImportJobResponse;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("${studio.features.skillgraph.web.import-jobs-base-path:/api/mgmt/skillgraph/datasets}")
public class SkillDatasetImportMgmtController {

    private final SkillDatasetImportJobService service;

    @PostMapping("/import-jobs")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public SkillDatasetImportJobResponse create(@Valid @RequestBody SkillDatasetImportRequest request) {
        return SkillDatasetImportJobResponse.from(service.create(new SkillDatasetImportCommand(
                request.provider(),
                request.datasetId(),
                request.datasetName(),
                request.version(),
                request.language(),
                request.sourceLocation())));
    }

    @GetMapping("/import-jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public SkillDatasetImportJobResponse get(@PathVariable @Size(max = 100) String jobId) {
        return SkillDatasetImportJobResponse.from(service.get(jobId));
    }

    @GetMapping({ "", "/import-jobs" })
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public List<SkillDatasetImportJobResponse> recent(
            @RequestParam(defaultValue = "20") int limit) {
        return service.recent(limit).stream()
                .map(SkillDatasetImportJobResponse::from)
                .toList();
    }

}
