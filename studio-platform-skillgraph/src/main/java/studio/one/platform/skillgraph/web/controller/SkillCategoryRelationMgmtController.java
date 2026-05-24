package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.PreviewSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryRelationsCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryGraphView;
import studio.one.platform.skillgraph.application.result.SkillCategoryRelationView;
import studio.one.platform.skillgraph.application.usecase.SkillCategoryRelationService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.category-relation-base-path:/api/mgmt/skillgraph/categories/relations}")
@RequiredArgsConstructor
@Validated
public class SkillCategoryRelationMgmtController {

    private final SkillCategoryRelationService service;

    @PostMapping("/preview")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillCategoryGraphView>> preview(
            @Valid @RequestBody(required = false) PreviewSkillCategoryRelationsCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.preview(command)));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillCategoryRelationView>>> list(
            @RequestParam(value = "categoryIds", required = false) List<@Size(max = 100) String> categoryIds) {
        return ResponseEntity.ok(ApiResponse.ok(service.findRelations(categoryIds)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<List<SkillCategoryRelationView>>> save(
            @Valid @RequestBody SaveSkillCategoryRelationsCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveRelations(command)));
    }

    @DeleteMapping("/{relationId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @Size(max = 100) String relationId) {
        service.deleteRelation(relationId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
