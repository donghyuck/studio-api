package studio.one.platform.ai.core.vector;

import java.util.List;
import java.util.Objects;

import studio.one.platform.ai.core.MetadataFilter;

/**
 * Encapsulates a similarity search query for a vector store.
 */
public final class VectorSearchRequest {

    private final List<Double> embedding;
    private final int topK;
    private final MetadataFilter metadataFilter;
    private final Double minScore;

    public VectorSearchRequest(List<Double> embedding, int topK) {
        this(embedding, topK, MetadataFilter.empty(), null);
    }

    public VectorSearchRequest(List<Double> embedding, int topK, MetadataFilter metadataFilter) {
        this(embedding, topK, metadataFilter, null);
    }

    public VectorSearchRequest(List<Double> embedding, int topK, MetadataFilter metadataFilter, Double minScore) {
        this.embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
        if (embedding.isEmpty()) {
            throw new IllegalArgumentException("Search embedding must not be empty");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        if (minScore != null && minScore < 0.0d) {
            throw new IllegalArgumentException("minScore must be greater than or equal to zero");
        }
        this.topK = topK;
        this.metadataFilter = metadataFilter == null ? MetadataFilter.empty() : metadataFilter;
        this.minScore = minScore;
    }

    public List<Double> embedding() {
        return embedding;
    }

    public int topK() {
        return topK;
    }

    public MetadataFilter metadataFilter() {
        return metadataFilter;
    }

    public Double minScore() {
        return minScore;
    }

    public boolean hasMinScore() {
        return minScore != null;
    }
}
