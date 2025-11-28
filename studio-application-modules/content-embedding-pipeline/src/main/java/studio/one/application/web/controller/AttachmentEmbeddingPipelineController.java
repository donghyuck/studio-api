package studio.one.application.web.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.web.dto.EmbeddingResponseDto;
import studio.one.platform.ai.web.dto.EmbeddingVectorDto;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.text.service.FileContentExtractionService;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.PREFIX + ".attachment.web.base-path:/api/mgmt/attachments}")
@RequiredArgsConstructor
@Slf4j
public class AttachmentEmbeddingPipelineController {

    private final AttachmentService attachmentService;
    private final ObjectProvider<FileContentExtractionService> textExtractionProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;

    @GetMapping("/{attachmentId:[\\p{Digit}]+}/embedding")
    @PreAuthorize("@endpointAuthz.can('features:attachment','write')")
    public ResponseEntity<ApiResponse<EmbeddingResponseDto>> embed(@PathVariable("attachmentId") long attachmentId)
            throws NotFoundException, IOException {

        FileContentExtractionService extractor = textExtractionProvider.getIfAvailable();
        if (extractor == null) {
            ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                    .message("Text extraction is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }

        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        if (embeddingPort == null) {
            ApiResponse<EmbeddingResponseDto> body = ApiResponse.<EmbeddingResponseDto>builder()
                    .message("Embedding provider is not configured")
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
        }

        Attachment attachment = attachmentService.getAttachmentById(attachmentId);
        try (InputStream in = attachmentService.getInputStream(attachment)) {
            String text = extractor.extractText(attachment.getContentType(), attachment.getName(), in);
            EmbeddingResponse response = embeddingPort.embed(new EmbeddingRequest(List.of(text)));
            EmbeddingResponseDto payload = new EmbeddingResponseDto(toEmbeddingVectors(response));
            return ResponseEntity.ok(ApiResponse.ok(payload));
        }
    }

    private List<EmbeddingVectorDto> toEmbeddingVectors(EmbeddingResponse response) {
        return response.vectors().stream()
                .map(vector -> new EmbeddingVectorDto(vector.referenceId(), List.copyOf(vector.values())))
                .toList();
    }
}
