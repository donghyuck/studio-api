package studio.one.platform.skillgraph.domain.model;

import java.time.Instant;

public record SkillSourceChunk(
        String sourceChunkId,
        String sourceType,
        String sourceId,
        String chunkId,
        String text,
        Instant createdAt) {

    public SkillSourceChunk {
        sourceChunkId = requireText(sourceChunkId, "sourceChunkId");
        sourceType = normalize(sourceType);
        sourceId = normalize(sourceId);
        chunkId = normalize(chunkId);
        text = requireText(text, "text");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
