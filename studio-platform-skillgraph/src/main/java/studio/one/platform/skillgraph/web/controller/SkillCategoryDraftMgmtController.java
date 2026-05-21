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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.GenerateSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveAndAssignSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftAssignmentResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryDraftService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.category-draft-base-path:/api/mgmt/skillgraph/category-drafts}")
@RequiredArgsConstructor
@Validated
public class SkillCategoryDraftMgmtController {

    private final SkillCategoryDraftService service;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillCategoryDraftResult>> generate(
            @RequestParam(value = "projectionId", defaultValue = "default") @Size(max = 100) String projectionId,
            @RequestParam(value = "clusterIds", required = false) List<@Size(max = 100) String> clusterIds,
            @RequestParam(value = "representativeLimit", defaultValue = "5") @Min(1) @Max(20) int representativeLimit,
            @RequestParam(value = "includeNoise", required = false, defaultValue = "false") boolean includeNoise,
            @RequestParam(value = "useLlm", required = false, defaultValue = "false") boolean useLlm) {
        return ResponseEntity.ok(ApiResponse.ok(service.generateDrafts(new GenerateSkillCategoryDraftCommand(
                projectionId,
                clusterIds,
                representativeLimit,
                includeNoise,
                useLlm))));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryDraftResult>> generate(
            @Valid @RequestBody GenerateSkillCategoryDraftCommand command,
            @RequestParam(value = "useLlm", required = false) Boolean useLlm) {
        GenerateSkillCategoryDraftCommand effectiveCommand = useLlm == null ? command
                : new GenerateSkillCategoryDraftCommand(
                        command.projectionId(),
                        command.clusterIds(),
                        command.representativeLimit(),
                        command.includeNoise(),
                        useLlm);
        return ResponseEntity.ok(ApiResponse.ok(service.generateDrafts(effectiveCommand)));
    }

    @PostMapping("/save")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<List<SkillCategoryView>>> save(
            @Valid @RequestBody SaveSkillCategoryDraftCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveDrafts(command)));
    }

    @PostMapping("/save-and-assign")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryDraftAssignmentResult>> saveAndAssign(
            @Valid @RequestBody SaveAndAssignSkillCategoryDraftCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveAndAssignDrafts(command)));
    }
}
