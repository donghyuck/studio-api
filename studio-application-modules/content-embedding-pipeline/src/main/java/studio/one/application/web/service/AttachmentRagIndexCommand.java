package studio.one.application.web.service;

import java.util.List;
import java.util.Map;

public class AttachmentRagIndexCommand {
    private final String documentId;
    private final String objectType;
    private final String objectId;
    private final Map<String, Object> metadata;
    private final List<String> keywords;
    private final boolean useLlmKeywordExtraction;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;

    public AttachmentRagIndexCommand(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }

    public AttachmentRagIndexCommand(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel) {
        this.documentId = normalize(documentId);
        this.objectType = normalize(objectType);
        this.objectId = normalize(objectId);
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

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
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
