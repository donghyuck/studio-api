package studio.one.platform.markdown.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.documentmetadata.DocumentMetadataArtifact;
import studio.one.platform.markdown.application.MarkdownDocumentMetadataService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${studio.markdown.web.base-path:/api/markdown-documents}")
public class MarkdownDocumentMetadataController {

    private final MarkdownDocumentMetadataService service;

    public MarkdownDocumentMetadataController(MarkdownDocumentMetadataService service) {
        this.service = service;
    }

    @GetMapping("/{id}/metadata")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read') "
            + "and @endpointAuthz.can('features:attachment','read')")
    public ApiResponse<DocumentMetadataArtifact> metadata(
            @PathVariable String id,
            @RequestParam(required = false) String revisionId) {
        return ApiResponse.ok(service.get(id, revisionId));
    }
}
