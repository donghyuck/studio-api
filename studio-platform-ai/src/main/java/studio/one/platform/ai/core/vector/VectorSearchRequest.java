package studio.one.platform.ai.core.vector;

import java.util.List;
import java.util.Objects;

import studio.one.platform.ai.core.MetadataFilter;

/**
 * Encapsulates a similarity search query for a vector store.
 */
public final class VectorSearchRequest {

    public static final int MAX_TOP_K = 100;
    public static final double MAX_MIN_SCORE = 1.0d;

    private final List<Double> embedding;
    private final String queryText;
    private final int topK;
    private final MetadataFilter metadataFilter;
    private final Double minScore;
    private final boolean includeText;
    private final boolean includeMetadata;

    public VectorSearchRequest(List<Double> embedding, int topK) {
        this(embedding, null, topK, MetadataFilter.empty(), null, true, true);
    }

    public VectorSearchRequest(List<Double> embedding, int topK, MetadataFilter metadataFilter) {
        this(embedding, null, topK, metadataFilter, null, true, true);
    }

    public VectorSearchRequest(List<Double> embedding, int topK, MetadataFilter metadataFilter, Double minScore) {
        this(embedding, null, topK, metadataFilter, minScore, true, true);
    }

    public VectorSearchRequest(
            List<Double> embedding,
            String queryText,
            int topK,
            MetadataFilter metadataFilter,
            Double minScore,
            boolean includeText,
            boolean includeMetadata) {
        this.embedding = List.copyOf(Objects.requireNonNull(embedding, "embedding"));
        if (embedding.isEmpty()) {
            throw new IllegalArgumentException("Search embedding must not be empty");
        }
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        if (minScore != null && (minScore < 0.0d || minScore > MAX_MIN_SCORE)) {
            throw new IllegalArgumentException("minScore must be between 0.0 and " + MAX_MIN_SCORE);
        }
        this.topK = topK;
        this.queryText = queryText == null || queryText.isBlank() ? null : queryText.trim();
        this.metadataFilter = metadataFilter == null ? MetadataFilter.empty() : metadataFilter;
        this.minScore = minScore;
        this.includeText = includeText;
        this.includeMetadata = includeMetadata;
    }

    /**
     * @deprecated since 2026-04-25. Use {@link #queryVector()}.
     */
    @Deprecated(forRemoval = false)
    public List<Double> embedding() {
        return queryVector();
    }

    public List<Double> queryVector() {
        return embedding;
    }

    public String queryText() {
        return queryText;
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

    public boolean includeText() {
        return includeText;
    }

    public boolean includeMetadata() {
        return includeMetadata;
    }
}
