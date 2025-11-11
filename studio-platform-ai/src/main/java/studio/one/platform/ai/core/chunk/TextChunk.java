package studio.one.platform.ai.core.chunk;

import java.util.Objects;

/**
 * Represents a chunk of text prepared for embedding.
 */
public final class TextChunk {

    private final String id;
    private final String content;

    public TextChunk(String id, String content) {
        this.id = Objects.requireNonNull(id, "id");
        this.content = Objects.requireNonNull(content, "content");
    }

    public String id() {
        return id;
    }

    public String content() {
        return content;
    }
}
