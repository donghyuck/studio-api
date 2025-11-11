package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;

/**
 * Request to index a document into the RAG pipeline.
 */
public final class RagIndexRequest {

    private final String documentId;
    private final String text;
    private final Map<String, Object> metadata;

    public RagIndexRequest(String documentId, String text, Map<String, Object> metadata) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.text = Objects.requireNonNull(text, "text");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String documentId() {
        return documentId;
    }

    public String text() {
        return text;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
