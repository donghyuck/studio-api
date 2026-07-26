package studio.one.platform.markdown.web;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import studio.one.platform.documentmetadata.DocumentMetadataSchema;
import studio.one.platform.documentmetadata.DocumentMetadataSchemaRegistry;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/document-metadata")
public class DocumentMetadataSchemaController {

    private final DocumentMetadataSchemaRegistry registry;

    public DocumentMetadataSchemaController(DocumentMetadataSchemaRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/schemas")
    @PreAuthorize("@endpointAuthz.can('features:markdown','read')")
    public ApiResponse<SchemaResponse> schemas() {
        return ApiResponse.ok(new SchemaResponse(registry.schemaVersion(), registry.schemas()));
    }

    public record SchemaResponse(String schemaVersion, List<DocumentMetadataSchema> schemas) {
    }
}
