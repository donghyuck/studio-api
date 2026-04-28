package studio.one.platform.ai.core.rag;

import java.util.Objects;

import studio.one.platform.ai.core.MetadataFilter;

/**
 * Request to query the RAG pipeline.
 */
public final class RagSearchRequest {

    public static final int MAX_TOP_K = 100;
    public static final double MAX_MIN_SCORE = 1.0d;

    private final String query;
    private final int topK;
    private final MetadataFilter metadataFilter;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;
    private final Double minScore;
    private final Integer requestedTopK;
    private final Double requestedMinScore;

    public RagSearchRequest(String query, int topK) {
        this(query, topK, MetadataFilter.empty());
    }

    public RagSearchRequest(String query, int topK, MetadataFilter metadataFilter) {
        this(query, topK, metadataFilter, null, null, null);
    }

    public RagSearchRequest(
            String query,
            int topK,
            MetadataFilter metadataFilter,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel) {
        this(query, topK, metadataFilter, embeddingProfileId, embeddingProvider, embeddingModel, null);
    }

    public RagSearchRequest(
            String query,
            int topK,
            MetadataFilter metadataFilter,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Double minScore) {
        this(query, topK, metadataFilter, embeddingProfileId, embeddingProvider, embeddingModel,
                minScore, topK, minScore);
    }

    public RagSearchRequest(
            String query,
            int topK,
            MetadataFilter metadataFilter,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Double minScore,
            Integer requestedTopK,
            Double requestedMinScore) {
        this.query = Objects.requireNonNull(query, "query");
        if (topK <= 0 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        if (minScore != null && (minScore < 0.0d || minScore > MAX_MIN_SCORE)) {
            throw new IllegalArgumentException("minScore must be between 0.0 and " + MAX_MIN_SCORE);
        }
        this.topK = topK;
        this.metadataFilter = metadataFilter == null ? MetadataFilter.empty() : metadataFilter;
        this.embeddingProfileId = normalize(embeddingProfileId);
        this.embeddingProvider = normalize(embeddingProvider);
        this.embeddingModel = normalize(embeddingModel);
        this.minScore = minScore;
        this.requestedTopK = requestedTopK;
        this.requestedMinScore = requestedMinScore;
    }

    public String query() {
        return query;
    }

    public int topK() {
        return topK;
    }

    public MetadataFilter metadataFilter() {
        return metadataFilter;
    }

    public String embeddingProfileId() {
        return embeddingProfileId;
    }

    public String embeddingProvider() {
        return embeddingProvider;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public Double minScore() {
        return minScore;
    }

    public Integer requestedTopK() {
        return requestedTopK;
    }

    public Double requestedMinScore() {
        return requestedMinScore;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
