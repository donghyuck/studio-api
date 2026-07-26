package studio.one.platform.markdown.autoconfigure;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

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

import studio.one.platform.markdown.autoconfigure.MarkdownMetadataBackfillService.CreateRequest;
import studio.one.platform.markdown.autoconfigure.MarkdownMetadataBackfillService.ItemView;
import studio.one.platform.markdown.autoconfigure.MarkdownMetadataBackfillService.JobView;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@Validated
@RequestMapping("/api/mgmt/markdown/metadata-backfill-jobs")
public class MarkdownMetadataBackfillController {

    private final MarkdownMetadataBackfillService service;

    public MarkdownMetadataBackfillController(MarkdownMetadataBackfillService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage') "
            + "and @endpointAuthz.can('services:ai_rag','write')")
    public ResponseEntity<ApiResponse<JobView>> create(
            @Valid @RequestBody CreateRequest request,
            Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(service.create(
                request, principal == null ? null : principal.getName())));
    }

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<List<JobView>>> list(
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(limit)));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<JobView>> get(
            @PathVariable @Size(max = 100) String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(jobId)));
    }

    @GetMapping("/{jobId}/items")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage')")
    public ResponseEntity<ApiResponse<List<ItemView>>> items(
            @PathVariable @Size(max = 100) String jobId,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(service.items(jobId, limit)));
    }

    @PostMapping("/{jobId}/retry")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage') "
            + "and @endpointAuthz.can('services:ai_rag','write')")
    public ResponseEntity<ApiResponse<JobView>> retry(
            @PathVariable @Size(max = 100) String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.retry(jobId)));
    }

    @PostMapping("/{jobId}/cancel")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage') "
            + "and @endpointAuthz.can('services:ai_rag','write')")
    public ResponseEntity<ApiResponse<JobView>> cancel(
            @PathVariable @Size(max = 100) String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.cancel(jobId)));
    }
}
