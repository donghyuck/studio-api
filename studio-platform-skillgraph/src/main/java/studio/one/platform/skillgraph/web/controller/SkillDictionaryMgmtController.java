package studio.one.platform.skillgraph.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.service.DuplicateSkillDictionaryException;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.web.dto.request.CreateSkillDictionaryRequest;
import studio.one.platform.skillgraph.web.dto.response.SkillDictionaryDto;
import studio.one.platform.skillgraph.web.dto.response.SkillDictionaryPageResponse;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.features.skillgraph.web.dictionary-base-path:/api/mgmt/skillgraph/dictionary}")
@RequiredArgsConstructor
@Validated
public class SkillDictionaryMgmtController {

    private final SkillDictionaryService dictionaryService;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillDictionaryPageResponse>> search(
            @RequestParam(value = "q", required = false) @Size(max = 200) String q,
            @RequestParam(value = "offset", required = false, defaultValue = "0") @Min(0) int offset,
            @RequestParam(value = "limit", required = false, defaultValue = "100") @Min(1) @Max(500) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(SkillDictionaryPageResponse.from(
                offset,
                limit,
                dictionaryService.search(q, offset, limit + 1))));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<SkillDictionaryDto>> create(
            @Valid @RequestBody CreateSkillDictionaryRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok(SkillDictionaryDto.from(dictionaryService.create(request.toCommand()))));
        } catch (DuplicateSkillDictionaryException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{skillId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillDictionaryDto>> get(@PathVariable @Size(max = 100) String skillId) {
        return ResponseEntity.ok(ApiResponse.ok(SkillDictionaryDto.from(dictionaryService.get(skillId))));
    }
}
