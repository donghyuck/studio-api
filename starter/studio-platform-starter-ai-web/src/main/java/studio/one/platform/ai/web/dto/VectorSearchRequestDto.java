package studio.one.platform.ai.web.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * DTO for searching the vector store.
 */
public class VectorSearchRequestDto {
    private final String query;
    private final List<Double> embedding;
    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 100, message = "topK must be at most 100")
    private final Integer topK;
    private final Boolean hybrid;
    private final String objectType;
    private final String objectId;
    @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
    private final Double minScore;
    private final Boolean includeText;
    private final Boolean includeMetadata;
    private final String embeddingProfileId;
    private final String embeddingProvider;
    private final String embeddingModel;

    @JsonCreator
    public VectorSearchRequestDto(
            @JsonProperty("query") String query,
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("hybrid") Boolean hybrid,
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("minScore") Double minScore,
            @JsonProperty("includeText") Boolean includeText,
            @JsonProperty("includeMetadata") Boolean includeMetadata,
            @JsonProperty("embeddingProfileId") String embeddingProfileId,
            @JsonProperty("embeddingProvider") String embeddingProvider,
            @JsonProperty("embeddingModel") String embeddingModel) {
        this.query = query;
        this.embedding = embedding;
        this.topK = topK;
        this.hybrid = hybrid == null ? Boolean.FALSE : hybrid;
        this.objectType = objectType;
        this.objectId = objectId;
        this.minScore = minScore;
        this.includeText = includeText == null ? Boolean.TRUE : includeText;
        this.includeMetadata = includeMetadata == null ? Boolean.TRUE : includeMetadata;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
    }

    public VectorSearchRequestDto(String query, List<Double> embedding, Integer topK, Boolean hybrid,
                                  String objectType, String objectId, Double minScore) {
        this(query, embedding, topK, hybrid, objectType, objectId, minScore, null, null, null, null, null);
    }

    public VectorSearchRequestDto(String query, List<Double> embedding, Integer topK, Boolean hybrid,
                                  String objectType, String objectId, Double minScore,
                                  Boolean includeText, Boolean includeMetadata) {
        this(query, embedding, topK, hybrid, objectType, objectId, minScore, includeText, includeMetadata,
                null, null, null);
    }

    public String query() { return query; }
    public List<Double> embedding() { return embedding; }
    public Integer topK() { return topK; }
    public Boolean hybrid() { return hybrid; }
    public String objectType() { return objectType; }
    public String objectId() { return objectId; }
    public Double minScore() { return minScore; }
    public Boolean includeText() { return includeText; }
    public Boolean includeMetadata() { return includeMetadata; }
    public String embeddingProfileId() { return embeddingProfileId; }
    public String embeddingProvider() { return embeddingProvider; }
    public String embeddingModel() { return embeddingModel; }

    public String getQuery() { return query; }
    public List<Double> getEmbedding() { return embedding; }
    public Integer getTopK() { return topK; }
    public Boolean getHybrid() { return hybrid; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public Double getMinScore() { return minScore; }
    public Boolean getIncludeText() { return includeText; }
    public Boolean getIncludeMetadata() { return includeMetadata; }
    public String getEmbeddingProfileId() { return embeddingProfileId; }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public String getEmbeddingModel() { return embeddingModel; }
}
