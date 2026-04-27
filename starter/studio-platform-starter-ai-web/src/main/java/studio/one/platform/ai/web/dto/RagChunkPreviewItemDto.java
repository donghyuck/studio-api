package studio.one.platform.ai.web.dto;

import java.util.Map;

public record RagChunkPreviewItemDto(
        String chunkId,
        String content,
        int contentLength,
        Integer chunkOrder,
        String chunkType,
        String parentChunkId,
        String previousChunkId,
        String nextChunkId,
        String headingPath,
        String section,
        String sourceRef,
        Integer page,
        Integer slide,
        Map<String, Object> metadata) {
}
