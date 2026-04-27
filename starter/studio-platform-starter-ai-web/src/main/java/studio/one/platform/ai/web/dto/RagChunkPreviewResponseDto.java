package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagChunkPreviewResponseDto(
        List<RagChunkPreviewItemDto> chunks,
        int totalChunks,
        int totalChars,
        String strategy,
        int maxSize,
        int overlap,
        String unit,
        List<String> warnings) {
}
