package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public class ProjectionSummaryResponse {

    private final String projectionId;

    private final String name;

    private final String algorithm;

    private final String status;

    private final List<String> targetTypes;

    private final int itemCount;

    private final Instant createdAt;

    private final Instant completedAt;

    @JsonCreator
    public ProjectionSummaryResponse(
            @JsonProperty("projectionId") String projectionId,
            @JsonProperty("name") String name,
            @JsonProperty("algorithm") String algorithm,
            @JsonProperty("status") String status,
            @JsonProperty("targetTypes") List<String> targetTypes,
            @JsonProperty("itemCount") int itemCount,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("completedAt") Instant completedAt
    ) {
        this.projectionId = projectionId;
        this.name = name;
        this.algorithm = algorithm;
        this.status = status;
        this.targetTypes = targetTypes;
        this.itemCount = itemCount;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String projectionId() {
        return projectionId;
    }

    public String getProjectionId() {
        return projectionId;
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String algorithm() {
        return algorithm;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String status() {
        return status;
    }

    public String getStatus() {
        return status;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public List<String> getTargetTypes() {
        return targetTypes;
    }

    public int itemCount() {
        return itemCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}