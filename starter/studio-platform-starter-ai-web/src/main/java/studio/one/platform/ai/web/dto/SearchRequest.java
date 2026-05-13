package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;

public class SearchRequest {

    @NotBlank
    private final String query;

    @Min(1)
    @Max(100)
    private final Integer topK;

    private final String objectType;

    private final String objectId;

    private final String embeddingProfileId;

    private final String embeddingProvider;

    private final String embeddingModel;

    @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
    private final Double minScore;

    @JsonCreator
    public SearchRequest(
            @JsonProperty("query") String query,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("objectType") String objectType,
            @JsonProperty("objectId") String objectId,
            @JsonProperty("embeddingProfileId") String embeddingProfileId,
            @JsonProperty("embeddingProvider") String embeddingProvider,
            @JsonProperty("embeddingModel") String embeddingModel,
            @JsonProperty("minScore") Double minScore
    ) {
        this.query = query;
        this.topK = topK;
        this.objectType = objectType;
        this.objectId = objectId;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.minScore = minScore;
    }

    public String query() {
        return query;
    }

    public String getQuery() {
        return query;
    }

    public Integer topK() {
        return topK;
    }

    public Integer getTopK() {
        return topK;
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

    public Double minScore() {
        return minScore;
    }

    public Double getMinScore() {
        return minScore;
    }

public SearchRequest(String query, int topK) {
        this(query, topK, null, null, null, null, null, null);
    }

    public SearchRequest(String query, int topK, String objectType, String objectId) {
        this(query, topK, objectType, objectId, null, null, null, null);
    }

}