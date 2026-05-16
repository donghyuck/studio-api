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
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.taxonomy-base-path:/api/mgmt/skillgraph/categories}")
@RequiredArgsConstructor
@Validated
public class SkillTaxonomyMgmtController {
    private final SkillTaxonomyService service;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillCategoryView>>> list(
            @RequestParam(value = "parentCategoryId", required = false) @Size(max = 100) String parentCategoryId,
            @RequestParam(value = "limit", required = false, defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(service.findCategories(parentCategoryId).stream()
                .limit(limit)
                .toList()));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryView>> save(@Valid @RequestBody SkillCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveCategory(command)));
    }
}
