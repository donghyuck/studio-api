package studio.one.platform.ai.core.vector;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a similarity search query for a vector store.
 */
public final class VectorSearchRequest {

    private final List<Double> embedding;
    private final int topK;

    public VectorSearchRequest(List<Double> embedding, int topK) {
        this.embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
        if (embedding.isEmpty()) {
            throw new IllegalArgumentException("Search embedding must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        this.topK = topK;
    }

    public List<Double> embedding() {
        return embedding;
    }

    public int topK() {
        return topK;
    }
}
