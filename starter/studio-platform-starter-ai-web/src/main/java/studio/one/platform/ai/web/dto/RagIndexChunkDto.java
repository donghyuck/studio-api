package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.Map;

public record RagIndexChunkDto(
        String chunkId,
        String documentId,
        String parentChunkId,
        Integer chunkOrder,
        String chunkType,
        String content,
        Double score,
        String headingPath,
        String sourceRef,
        Integer page,
        Integer slide,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant indexedAt) {
}
