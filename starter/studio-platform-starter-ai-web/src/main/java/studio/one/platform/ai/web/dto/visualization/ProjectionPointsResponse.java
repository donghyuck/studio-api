package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ProjectionPointsResponse {

    private final String projectionId;

    private final String algorithm;

    private final long totalCount;

    private final List<ProjectionPointResponse> items;

    @JsonCreator
    public ProjectionPointsResponse(
            @JsonProperty("projectionId") String projectionId,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("totalCount") long totalCount,
            @JsonProperty("items") List<ProjectionPointResponse> items
    ) {
        this.projectionId = projectionId;
        this.algorithm = algorithm;
        this.totalCount = totalCount;
        this.items = items;
    }

    public String projectionId() {
        return projectionId;
    }

    public String getProjectionId() {
        return projectionId;
    }

    public String algorithm() {
        return algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public long totalCount() {
        return totalCount;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public List<ProjectionPointResponse> items() {
        return items;
    }

    public List<ProjectionPointResponse> getItems() {
        return items;
    }

}