package studio.one.platform.ai.core.vector;

import java.util.List;

/**
 * Aggregate RAG vector search response.
 */
public final class VectorSearchResults {

    private final List<VectorSearchHit> hits;
    private final long elapsedMs;

    public VectorSearchResults(List<VectorSearchHit> hits, long elapsedMs) {
        if (elapsedMs < 0L) {
            throw new IllegalArgumentException("elapsedMs must not be negative");
        }
        this.hits = hits == null ? List.of() : List.copyOf(hits);
        this.elapsedMs = elapsedMs;
    }

    public static VectorSearchResults of(List<VectorSearchHit> hits, long elapsedMs) {
        return new VectorSearchResults(hits, elapsedMs);
    }

    public List<VectorSearchHit> hits() {
        return hits;
    }

    public long elapsedMs() {
        return elapsedMs;
    }
}
