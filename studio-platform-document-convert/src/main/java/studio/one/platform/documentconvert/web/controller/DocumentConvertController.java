package studio.one.platform.documentconvert.web.controller;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.platform.documentconvert.application.result.DocumentConvertJobResult;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.documentconvert.web.dto.DocumentConvertRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.document-convert.web.base-path:/api/document-conversions}")
public class DocumentConvertController {
    private final DocumentConvertService service;

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('features:document-convert','manage')")
    public ResponseEntity<ApiResponse<DocumentConvertJobResult>> create(
            @Valid @RequestBody DocumentConvertRequest request, Authentication authentication) {
        String actor = authentication == null ? null : authentication.getName();
        return ResponseEntity.accepted().body(ApiResponse.ok(service.create(request.sourceFileId(),
                request.sourceFormat(), request.targetFormat(), request.options(), actor)));
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:document-convert','read')")
    public ResponseEntity<ApiResponse<DocumentConvertJobResult>> get(@PathVariable String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(jobId)));
    }

    @GetMapping("/{jobId}/download")
    @PreAuthorize("@endpointAuthz.can('features:document-convert','read')")
    public ResponseEntity<Void> download(@PathVariable String jobId) {
        URI location = service.download(jobId);
        return ResponseEntity.status(302).location(location).build();
    }

    @PostMapping("/{jobId}/retry")
    @PreAuthorize("@endpointAuthz.can('features:document-convert','manage')")
    public ResponseEntity<ApiResponse<DocumentConvertJobResult>> retry(@PathVariable String jobId) {
        return ResponseEntity.accepted().body(ApiResponse.ok(service.retry(jobId)));
    }

    @DeleteMapping("/{jobId}")
    @PreAuthorize("@endpointAuthz.can('features:document-convert','manage')")
    public ResponseEntity<ApiResponse<DocumentConvertJobResult>> cancel(@PathVariable String jobId) {
        return ResponseEntity.ok(ApiResponse.ok(service.cancel(jobId)));
    }
}
