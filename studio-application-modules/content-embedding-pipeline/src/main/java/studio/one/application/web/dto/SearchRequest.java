package studio.one.application.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SearchRequest {
    @NotBlank
    private String query;
    @Min(1)
    @Max(100)
    private Integer topK;
    private String objectType;
    private String objectId;
    private String embeddingProfileId;
    private String embeddingProvider;
    private String embeddingModel;
    @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
    @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
    private Double minScore;

    public SearchRequest() {
    }

    public SearchRequest(
            String query,
            Integer topK,
            String objectType,
            String objectId,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Double minScore) {
        this.query = query;
        this.topK = topK;
        this.objectType = objectType;
        this.objectId = objectId;
        this.embeddingProfileId = embeddingProfileId;
        this.embeddingProvider = embeddingProvider;
        this.embeddingModel = embeddingModel;
        this.minScore = minScore;
    }

    public SearchRequest(String query, int topK) {
        this(query, topK, null, null, null, null, null, null);
    }

    public SearchRequest(String query, int topK, String objectType, String objectId) {
        this(query, topK, objectType, objectId, null, null, null, null);
    }

    public String query() {
        return query;
    }

    public Integer topK() {
        return topK;
    }

    public String objectType() {
        return objectType;
    }

    public String objectId() {
        return objectId;
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

    public Double minScore() {
        return minScore;
    }
}
