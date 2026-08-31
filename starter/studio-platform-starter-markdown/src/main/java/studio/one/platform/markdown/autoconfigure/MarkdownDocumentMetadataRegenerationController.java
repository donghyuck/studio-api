package studio.one.platform.markdown.autoconfigure;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.markdown.web.base-path:/api/markdown-documents}")
public class MarkdownDocumentMetadataRegenerationController {

    private final MarkdownMetadataBackfillService service;

    public MarkdownDocumentMetadataRegenerationController(MarkdownMetadataBackfillService service) {
        this.service = service;
    }

    @PostMapping("/{id}/metadata/reextract")
    @PreAuthorize("@endpointAuthz.can('features:markdown','manage') "
            + "and @endpointAuthz.can('services:ai_rag','write')")
    public ApiResponse<DocumentMetadataArtifact> reextract(
            @PathVariable String id,
            @RequestParam(required = false) String revisionId) {
        return ApiResponse.ok(service.regenerate(id, revisionId));
    }
}
