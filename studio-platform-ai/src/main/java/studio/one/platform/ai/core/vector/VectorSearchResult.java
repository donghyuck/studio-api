package studio.one.platform.ai.core.vector;

import java.util.Objects;

/**
 * Response entry returned from a similarity search.
 */
public final class VectorSearchResult {

    private final VectorDocument document;
    private final double score;

    public VectorSearchResult(VectorDocument document, double score) {
        this.document = Objects.requireNonNull(document, "document");
        this.score = score;
    }

    public VectorDocument document() {
        return document;
    }

    public double score() {
        return score;
    }
}
