package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;

/**
 * Result returned from a RAG query.
 */
public final class RagSearchResult {

    private final String documentId;
    private final String content;
    private final Map<String, Object> metadata;
    private final double score;

    public RagSearchResult(String documentId, String content, Map<String, Object> metadata, double score) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.content = Objects.requireNonNull(content, "content");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.score = score;
    }

    public String documentId() {
        return documentId;
    }

    public String content() {
        return content;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public double score() {
        return score;
    }
}
