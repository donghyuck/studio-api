package studio.one.platform.skillgraph.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.AssignCategoryFromClusterCommand;
import studio.one.platform.skillgraph.application.command.AssignSkillsToCategoryCommand;
import studio.one.platform.skillgraph.application.command.BulkSkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.MergeSkillCategoriesCommand;
import studio.one.platform.skillgraph.application.command.MoveSkillCategoryCommand;
import studio.one.platform.skillgraph.application.command.SkillCategoryCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryHistoryView;
import studio.one.platform.skillgraph.application.result.SkillCategoryMutationResult;
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
    public ResponseEntity<ApiResponse<Page<SkillCategoryView>>> list(
            @RequestParam(value = "q", required = false) @Size(max = 200) String q,
            @RequestParam(value = "parentCategoryId", required = false) @Size(max = 100) String parentCategoryId,
            @PageableDefault(size = 50, sort = { "displayOrder", "name" }) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.searchCategories(q, parentCategoryId, pageable)));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillCategoryView>> get(
            @PathVariable @Size(max = 100) String categoryId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getCategory(categoryId)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryView>> save(@Valid @RequestBody SkillCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveCategory(command)));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryView>> update(
            @PathVariable @Size(max = 100) String categoryId,
            @Valid @RequestBody SkillCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveCategory(new SkillCategoryCommand(
                categoryId,
                command.parentCategoryId(),
                command.name(),
                command.displayOrder()))));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @Size(max = 100) String categoryId) {
        service.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{categoryId}/move")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryView>> move(
            @PathVariable @Size(max = 100) String categoryId,
            @Valid @RequestBody MoveSkillCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.moveCategory(categoryId, command)));
    }

    @PostMapping("/{categoryId}/skills")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryMutationResult>> assignSkills(
            @PathVariable @Size(max = 100) String categoryId,
            @Valid @RequestBody AssignSkillsToCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.assignSkills(categoryId, command)));
    }

    @PostMapping("/{categoryId}/assign-from-cluster")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryMutationResult>> assignFromCluster(
            @PathVariable @Size(max = 100) String categoryId,
            @Valid @RequestBody AssignCategoryFromClusterCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.assignFromCluster(categoryId, command)));
    }

    @PostMapping("/bulk")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryMutationResult>> bulk(
            @Valid @RequestBody BulkSkillCategoryCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.bulk(command)));
    }

    @PostMapping("/merge")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillCategoryMutationResult>> merge(
            @Valid @RequestBody MergeSkillCategoriesCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.mergeCategories(command)));
    }

    @GetMapping("/{categoryId}/history")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillCategoryHistoryView>>> categoryHistory(
            @PathVariable @Size(max = 100) String categoryId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.findCategoryHistory(categoryId, pageable)));
    }

}
