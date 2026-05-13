package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class ProjectionPointResponse {

    private final String vectorItemId;

    private final String targetType;

    private final String sourceId;

    private final String label;

    private final double x;

    private final double y;

    private final String clusterId;

    private final Map<String, Object> metadata;

    @JsonCreator
    public ProjectionPointResponse(
            @JsonProperty("vectorItemId") String vectorItemId,
            @JsonProperty("targetType") String targetType,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("label") String label,
            @JsonProperty("x") double x,
            @JsonProperty("y") double y,
            @JsonProperty("clusterId") String clusterId,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {
        this.vectorItemId = vectorItemId;
        this.targetType = targetType;
        this.sourceId = sourceId;
        this.label = label;
        this.x = x;
        this.y = y;
        this.clusterId = clusterId;
        this.metadata = metadata;
    }

    public String vectorItemId() {
        return vectorItemId;
    }

    public String getVectorItemId() {
        return vectorItemId;
    }

    public String targetType() {
        return targetType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String sourceId() {
        return sourceId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String label() {
        return label;
    }

    public String getLabel() {
        return label;
    }

    public double x() {
        return x;
    }

    public double getX() {
        return x;
    }

    public double y() {
        return y;
    }

    public double getY() {
        return y;
    }

    public String clusterId() {
        return clusterId;
    }

    public String getClusterId() {
        return clusterId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

}