package studio.one.platform.ai.core.embedding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Value object describing a batch embedding request.
 */
public final class EmbeddingRequest {

    private final List<String> texts;

    public EmbeddingRequest(List<String> texts) {
        Objects.requireNonNull(texts, "texts");
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("At least one text must be provided for embedding");
        }
        this.texts = List.copyOf(texts);
    }

    public List<String> texts() {
        return Collections.unmodifiableList(texts);
    }
}
