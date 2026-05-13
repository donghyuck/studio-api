package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class VectorSearchVisualizationRequest {

    @NotBlank
    private final String projectionId;

    @NotBlank
    @Size(max = 2000)
    private final String query;

    private final List<String> targetTypes;

    private final Integer topK;

    private final Double minScore;

    @JsonCreator
    public VectorSearchVisualizationRequest(
            @JsonProperty("projectionId") String projectionId,
            @JsonProperty("query") String query,
            @JsonProperty("targetTypes") List<String> targetTypes,
            @JsonProperty("topK") Integer topK,
            @JsonProperty("minScore") Double minScore
    ) {
        this.projectionId = projectionId;
        this.query = query;
        this.targetTypes = targetTypes;
        this.topK = topK;
        this.minScore = minScore;
    }

    public String projectionId() {
        return projectionId;
    }

    public String getProjectionId() {
        return projectionId;
    }

    public String query() {
        return query;
    }

    public String getQuery() {
        return query;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public List<String> getTargetTypes() {
        return targetTypes;
    }

    public Integer topK() {
        return topK;
    }

    public Integer getTopK() {
        return topK;
    }

    public Double minScore() {
        return minScore;
    }

    public Double getMinScore() {
        return minScore;
    }

}