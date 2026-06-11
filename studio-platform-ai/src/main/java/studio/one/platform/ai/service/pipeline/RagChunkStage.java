package studio.one.platform.ai.service.pipeline;

import java.time.Instant;
import java.util.Map;

public record RagChunkStage(
        String objectType,
        String objectId,
        String documentId,
        int chunkIndex,
        String chunkId,
        String text,
        Map<String, Object> metadata,
        Instant createdAt) {

    public RagChunkStage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
