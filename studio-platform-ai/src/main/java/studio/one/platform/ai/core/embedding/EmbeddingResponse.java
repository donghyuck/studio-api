package studio.one.platform.ai.core.embedding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable response containing embedding vectors.
 */
public final class EmbeddingResponse {

    private final List<EmbeddingVector> vectors;

    public EmbeddingResponse(List<EmbeddingVector> vectors) {
        Objects.requireNonNull(vectors, "vectors");
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("Embedding response must contain at least one vector");
        }
        this.vectors = List.copyOf(vectors);
    }

    public List<EmbeddingVector> vectors() {
        return Collections.unmodifiableList(vectors);
    }
}
