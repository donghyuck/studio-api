package studio.one.platform.skillgraph.web.controller;

import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.SkillCategoryHistoryView;
import studio.one.platform.skillgraph.application.usecase.SkillTaxonomyService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.skill-base-path:/api/mgmt/skillgraph/skills}")
@RequiredArgsConstructor
@Validated
public class SkillCategoryHistoryMgmtController {

    private final SkillTaxonomyService service;

    @GetMapping("/{skillId}/category-history")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<Page<SkillCategoryHistoryView>>> skillHistory(
            @PathVariable @Size(max = 100) String skillId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(service.findSkillCategoryHistory(skillId, pageable)));
    }
}
