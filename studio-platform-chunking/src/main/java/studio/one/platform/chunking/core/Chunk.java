package studio.one.platform.chunking.core;

import java.util.Objects;

/**
 * Immutable chunk content and metadata generated from a source document.
 */
public record Chunk(String id, String content, ChunkMetadata metadata) {

    public Chunk {
        if (isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (isBlank(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    }

    public static Chunk of(String id, String content, ChunkMetadata metadata) {
        return new Chunk(id, content, metadata);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
