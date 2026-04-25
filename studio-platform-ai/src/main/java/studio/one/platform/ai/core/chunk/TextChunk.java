package studio.one.platform.ai.core.chunk;

import java.util.Objects;

/**
 * Represents a chunk of text prepared for embedding.
 *
 * @deprecated since 2.x. Use {@code studio.one.platform.chunking.core.Chunk}
 * and {@code studio.one.platform.chunking.core.ChunkingOrchestrator} from
 * {@code studio-platform-chunking} for new RAG indexing code. This type remains
 * only for the legacy {@code starter-ai} fallback path.
 */
@Deprecated(since = "2.x", forRemoval = false)
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
