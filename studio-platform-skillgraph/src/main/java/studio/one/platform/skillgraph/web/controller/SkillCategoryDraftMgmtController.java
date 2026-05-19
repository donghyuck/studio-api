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
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
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
            @RequestParam(value = "representativeLimit", defaultValue = "5") @Min(1) @Max(20) int representativeLimit) {
        return ResponseEntity.ok(ApiResponse.ok(service.generateDrafts(projectionId, representativeLimit)));
    }

    @PostMapping("/save")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<List<SkillCategoryView>>> save(
            @Valid @RequestBody SaveSkillCategoryDraftCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveDrafts(command)));
    }
}
