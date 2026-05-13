package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotBlank;

public class RagIndexJobCreateRequestDto {

    @NotBlank
    private final String objectType;

    @NotBlank
    private final String objectId;

    private final String documentId;

    private final String sourceType;

    private final Boolean forceReindex;

    private final String text;

    private final Map<String, Object> metadata;

    private final List<String> keywords;

    private final Boolean useLlmKeywordExtraction;

    private final String embeddingProfileId;

    private final String embeddingProvider;

    private final String embeddingModel;

    private final String sourceName;

    @JsonCreator
    public RagIndexJobCreateRequestDto(
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("sourceType") String sourceType,
            @JsonProperty("forceReindex") Boolean forceReindex,
            @JsonProperty("text") String text,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("useLlmKeywordExtraction") Boolean useLlmKeywordExtraction,
            @JsonProperty("embeddingProfileId") String embeddingProfileId,
            @JsonProperty("embeddingProvider") String embeddingProvider,
            @JsonProperty("embeddingModel") String embeddingModel,
            @JsonProperty("sourceName") String sourceName
    ) {
        this.objectType = objectType;
        this.objectId = objectId;
        this.documentId = documentId;
        this.sourceType = sourceType;
        this.forceReindex = forceReindex;
        this.text = text;
        this.metadata = metadata;
        this.keywords = keywords;
        this.useLlmKeywordExtraction = useLlmKeywordExtraction;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.sourceName = sourceName;
    }

    public String objectType() {
        return objectType;
    }

    public String getObjectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Boolean forceReindex() {
        return forceReindex;
    }

    public Boolean getForceReindex() {
        return forceReindex;
    }

    public String text() {
        return text;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public List<String> keywords() {
        return keywords;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public Boolean useLlmKeywordExtraction() {
        return useLlmKeywordExtraction;
    }

    public Boolean getUseLlmKeywordExtraction() {
        return useLlmKeywordExtraction;
    }

    public String embeddingProfileId() {
        return embeddingProfileId;
    }

    public String getEmbeddingProfileId() {
        return embeddingProfileId;
    }

    public String embeddingProvider() {
        return embeddingProvider;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String sourceName() {
        return sourceName;
    }

    public String getSourceName() {
        return sourceName;
    }

public RagIndexJobCreateRequestDto(
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            Boolean forceReindex,
            String text,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(objectType, objectId, documentId, sourceType, forceReindex, text, metadata, keywords,
                useLlmKeywordExtraction, null, null, null, null);
    }

}