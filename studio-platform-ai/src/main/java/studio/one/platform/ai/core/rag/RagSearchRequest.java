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

    public RagSearchRequest(String query, int topK) {
        this(query, topK, MetadataFilter.empty());
    }

    public RagSearchRequest(String query, int topK, MetadataFilter metadataFilter) {
        this.query = Objects.requireNonNull(query, "query");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        this.topK = topK;
        this.metadataFilter = metadataFilter == null ? MetadataFilter.empty() : metadataFilter;
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
}
