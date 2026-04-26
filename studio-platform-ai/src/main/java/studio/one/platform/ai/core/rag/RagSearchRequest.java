package studio.one.platform.ai.core.rag;

import java.util.Objects;

import studio.one.platform.ai.core.MetadataFilter;

/**
 * Request to query the RAG pipeline.
 */
public final class RagSearchRequest {

    private final String query;
    private final int topK;
    private final MetadataFilter metadataFilter;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;

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
        this.query = Objects.requireNonNull(query, "query");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        this.topK = topK;
        this.metadataFilter = metadataFilter == null ? MetadataFilter.empty() : metadataFilter;
        this.embeddingProfileId = normalize(embeddingProfileId);
        this.embeddingProvider = normalize(embeddingProvider);
        this.embeddingModel = normalize(embeddingModel);
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

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
