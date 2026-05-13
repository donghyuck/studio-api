package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ProjectionListResponse {

    private final List<ProjectionSummaryResponse> items;

    @JsonCreator
    public ProjectionListResponse(@JsonProperty("items") List<ProjectionSummaryResponse> items) {
        this.items = items;
    }

    public List<ProjectionSummaryResponse> items() {
        return items;
    }

    public List<ProjectionSummaryResponse> getItems() {
        return items;
    }

}