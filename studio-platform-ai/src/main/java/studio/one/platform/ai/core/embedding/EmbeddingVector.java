package studio.one.platform.ai.core.embedding;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Embedding vector with optional reference identifier.
 */
public final class EmbeddingVector {

    private final String referenceId;
    private final List<Double> values;

    public EmbeddingVector(String referenceId, List<Double> values) {
        this.referenceId = referenceId;
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Embedding values cannot be empty");
        }
        this.values = List.copyOf(values);
    }

    public String referenceId() {
        return referenceId;
    }

    public List<Double> values() {
        return Collections.unmodifiableList(values);
    }
}
