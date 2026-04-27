package studio.one.platform.ai.web.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RagChunkPreviewRequestDto(
        @NotBlank String text,
        String documentId,
        String objectType,
        String objectId,
        String contentType,
        String filename,
        String strategy,
        Integer maxSize,
        Integer overlap,
        String unit,
        Map<String, Object> metadata) {
}
