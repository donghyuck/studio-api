package studio.one.platform.chunking.core;

import java.util.Objects;

/**
 * Immutable chunk content and metadata generated from a source document.
 */
public class Chunk {
    private final String id;
    private final String content;
    private final ChunkMetadata metadata;


    public Chunk(
            String id,
            String content,
            ChunkMetadata metadata) {
        if (isBlank(id)) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (isBlank(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
    
        this.id = id;
        this.content = content;
        this.metadata = metadata;
    }

    public static Chunk of(String id, String content, ChunkMetadata metadata) {
        return new Chunk(id, content, metadata);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String id() {
        return id;
    }

    public String content() {
        return content;
    }

    public ChunkMetadata metadata() {
        return metadata;
    }
}
