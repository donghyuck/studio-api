package studio.one.platform.ai.core.rag;

import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * Request to index a document into the RAG pipeline.
 */
public final class RagIndexRequest {

    private final String documentId;
    private final String text;
    private final Map<String, Object> metadata;
    private final List<String> keywords;
    private final boolean useLlmKeywordExtraction;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;

    public RagIndexRequest(String documentId, String text, Map<String, Object> metadata) {
        this(documentId, text, metadata, List.of(), false);
    }

    public RagIndexRequest(String documentId,
                           String text,
                           Map<String, Object> metadata,
                           List<String> keywords,
                           boolean useLlmKeywordExtraction) {
        this(documentId, text, metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }

    public RagIndexRequest(String documentId,
                           String text,
                           Map<String, Object> metadata,
                           List<String> keywords,
                           boolean useLlmKeywordExtraction,
                           String embeddingProfileId,
                           String embeddingProvider,
                           String embeddingModel) {
        this.documentId = Objects.requireNonNull(documentId, "documentId");
        this.text = Objects.requireNonNull(text, "text");
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.useLlmKeywordExtraction = useLlmKeywordExtraction;
        this.embeddingProfileId = normalize(embeddingProfileId);
        this.embeddingProvider = normalize(embeddingProvider);
        this.embeddingModel = normalize(embeddingModel);
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

    public List<String> keywords() {
        return keywords;
    }

    public boolean useLlmKeywordExtraction() {
        return useLlmKeywordExtraction;
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
