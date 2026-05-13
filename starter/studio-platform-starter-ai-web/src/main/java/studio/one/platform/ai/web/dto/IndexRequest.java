package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

public class IndexRequest {

    @NotBlank
    private final String documentId;

    @NotBlank
    private final String text;

    private final Map<String, Object> metadata;

    private final List<String> keywords;

    private final Boolean useLlmKeywordExtraction;

    private final String embeddingProfileId;

    private final String embeddingProvider;

    private final String embeddingModel;

    @JsonCreator
    public IndexRequest(
            @JsonProperty("documentId") String documentId,
            @JsonProperty("text") String text,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("keywords") List<String> keywords,
            @JsonProperty("useLlmKeywordExtraction") Boolean useLlmKeywordExtraction,
            @JsonProperty("embeddingProfileId") String embeddingProfileId,
            @JsonProperty("embeddingProvider") String embeddingProvider,
            @JsonProperty("embeddingModel") String embeddingModel
    ) {
        this.documentId = documentId;
        this.text = text;
        this.metadata = metadata;
        this.keywords = keywords;
        this.useLlmKeywordExtraction = useLlmKeywordExtraction;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
    }

    public String documentId() {
        return documentId;
    }

    public String getDocumentId() {
        return documentId;
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

public IndexRequest(
            String documentId,
            String text,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(documentId, text, metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }

}