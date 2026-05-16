package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.Valid;
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
import studio.one.platform.skillgraph.application.command.SkillRelationCommand;
import studio.one.platform.skillgraph.application.result.SkillRelationView;
import studio.one.platform.skillgraph.application.usecase.SkillGraphService;
import studio.one.platform.skillgraph.domain.model.SkillRelationType;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.graph-base-path:/api/mgmt/skillgraph/relations}")
@RequiredArgsConstructor
@Validated
public class SkillGraphMgmtController {
    private final SkillGraphService service;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillRelationView>>> list(
            @RequestParam(value = "skillId", required = false) @Size(max = 100) String skillId,
            @RequestParam(value = "type", required = false) SkillRelationType type) {
        return ResponseEntity.ok(ApiResponse.ok(service.findRelations(skillId, type)));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillRelationView>> save(@Valid @RequestBody SkillRelationCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveRelation(command)));
    }
}
