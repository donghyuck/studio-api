package studio.one.platform.ai.core.vector;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a document persisted in a vector store.
 */
public final class VectorDocument {

    private final String id;
    private final String content;
    private final Map<String, Object> metadata;
    private final List<Double> embedding;

    public VectorDocument(String id, String content, Map<String, Object> metadata, List<Double> embedding) {
        this.id = Objects.requireNonNull(id, "id");
        this.content = Objects.requireNonNull(content, "content");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
    }

    public String id() {
        return id;
    }

    public String content() {
        return content;
    }

    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public List<Double> embedding() {
        return Collections.unmodifiableList(embedding);
    }
}
