package studio.one.platform.skillgraph.web.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.usecase.SkillExtractionService;
import studio.one.platform.skillgraph.web.dto.request.SkillExtractionSimulationRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillExtractionResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.simulation-base-path:/api/mgmt/skillgraph/simulations}")
@RequiredArgsConstructor
@Validated
public class SkillGraphSimulationMgmtController {

    private final SkillExtractionService extractionService;

    @PostMapping("/extraction")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillExtractionResponse>> simulateExtraction(
            @Valid @RequestBody SkillExtractionSimulationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(SkillExtractionResponse.from(extractionService.dryRun(
                new SkillExtractionCommand(request.sourceType(), request.sourceId(), request.chunkId(), request.text())))));
    }
}
