package studio.one.platform.skillgraph.web.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.web.dto.response.SkillDictionaryDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.dictionary-base-path:/api/mgmt/skillgraph/dictionary}")
@RequiredArgsConstructor
@Validated
public class SkillDictionaryMgmtController {

    private final SkillDictionaryService dictionaryService;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<List<SkillDictionaryDto>>> search(
            @RequestParam(value = "q", required = false) @Size(max = 200) String q,
            @RequestParam(value = "limit", required = false, defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(dictionaryService.search(q, limit).stream()
                .map(SkillDictionaryDto::from)
                .toList()));
    }

    @GetMapping("/{skillId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillDictionaryDto>> get(@PathVariable @Size(max = 100) String skillId) {
        return ResponseEntity.ok(ApiResponse.ok(SkillDictionaryDto.from(dictionaryService.get(skillId))));
    }
}
