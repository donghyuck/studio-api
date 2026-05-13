package studio.one.application.web.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * RAG 색인 시 외부에서 objectType/objectId/documentId 등을 전달받기 위한 DTO.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AttachmentRagIndexRequestDto {
    private String documentId;
    private String objectType;
    private String objectId;
    private Map<String, Object> metadata;
    private List<String> keywords;
    private Boolean useLlmKeywordExtraction;
    private String embeddingProfileId;
    private String embeddingProvider;
    private String embeddingModel;
    private Boolean debug;

    public AttachmentRagIndexRequestDto() {
    }

    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Boolean debug) {
        this.documentId = documentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.metadata = metadata;
        this.keywords = keywords;
        this.useLlmKeywordExtraction = useLlmKeywordExtraction;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.debug = debug;
    }

    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction, null, null, null, null);
    }

    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction,
            Boolean debug) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction, null, null, null, debug);
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

    public Boolean useLlmKeywordExtraction() {
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

    public Boolean debug() {
        return debug;
    }
}
