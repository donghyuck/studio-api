package studio.one.platform.ai.core.rag;

import java.util.Objects;

/**
 * Request to query the RAG pipeline.
 */
public final class RagSearchRequest {

    private final String query;
    private final int topK;

    public RagSearchRequest(String query, int topK) {
        this.query = Objects.requireNonNull(query, "query");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        this.topK = topK;
    }

    public String query() {
        return query;
    }

    public int topK() {
        return topK;
    }
}
