package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
public class ProjectionCreateResponse {

    private final String projectionId;

    private final String status;

    private final String message;

    @JsonCreator
    public ProjectionCreateResponse(
            @JsonProperty("projectionId") String projectionId,
            @JsonProperty("status") String status,
            @JsonProperty("message") String message
    ) {
        this.projectionId = projectionId;
        this.status = status;
        this.message = message;
    }

    public String projectionId() {
        return projectionId;
    }

    public String getProjectionId() {
        return projectionId;
    }

    public String status() {
        return status;
    }

    public String getStatus() {
        return status;
    }

    public String message() {
        return message;
    }

    public String getMessage() {
        return message;
    }

}