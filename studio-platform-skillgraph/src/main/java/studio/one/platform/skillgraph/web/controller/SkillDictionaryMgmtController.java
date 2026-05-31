package studio.one.platform.skillgraph.web.controller;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.application.result.SkillDictionaryView;
import studio.one.platform.skillgraph.application.service.DuplicateSkillDictionaryException;
import studio.one.platform.skillgraph.application.usecase.SkillDictionaryService;
import studio.one.platform.skillgraph.web.dto.request.CreateSkillDictionaryRequest;
import studio.one.platform.skillgraph.web.dto.request.SkillDictionaryEmbeddingRequest;
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
    public ResponseEntity<ApiResponse<Page<SkillDictionaryView>>> search(
            @RequestParam(value = "q", required = false) Optional<String> q,
            @RequestParam(value = "status", required = false) Optional<String> status,
            @RequestParam(value = "categoryId", required = false) Optional<String> categoryId,
            @PageableDefault(size = 15, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<SkillDictionaryView> page = dictionaryService.search(
                q.map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElse(null),
                status.map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElse(null),
                categoryId.map(String::trim)
                        .filter(value -> !value.isBlank())
                        .orElse(null),
                pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
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

    @PostMapping("/embeddings/missing")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','manage')")
    public ResponseEntity<ApiResponse<?>> embedMissing(
            @Valid @RequestBody(required = false) SkillDictionaryEmbeddingRequest request) {
        int limit = request == null || request.limit() == null ? 500 : request.limit();
        return ResponseEntity.ok(ApiResponse.ok(dictionaryService.embedMissing(
                request == null ? null : request.embeddingProvider(),
                request == null ? null : request.embeddingModel(),
                request == null || request.embeddingDim() == null ? 0 : request.embeddingDim(),
                limit)));
    }

    @GetMapping("/embeddings/jobs/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<?>> getEmbeddingJob(@PathVariable @Size(max = 120) String jobId) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(dictionaryService.getEmbeddingJob(jobId)));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{skillId}")
    @PreAuthorize("@endpointAuthz.can('features:skillgraph','read')")
    public ResponseEntity<ApiResponse<SkillDictionaryDto>> get(@PathVariable @Size(max = 100) String skillId) {
        return ResponseEntity.ok(ApiResponse.ok(SkillDictionaryDto.from(dictionaryService.get(skillId))));
    }
}
