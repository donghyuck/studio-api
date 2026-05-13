package studio.one.platform.ai.web.dto.visualization;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class VectorItemDetailResponse {

    private final String vectorItemId;

    private final String targetType;

    private final String sourceId;

    private final String label;

    private final String text;

    private final String embeddingModel;

    private final Integer dimension;

    private final Map<String, Object> metadata;

    private final Instant createdAt;

    @JsonCreator
    public VectorItemDetailResponse(
            @JsonProperty("vectorItemId") String vectorItemId,
            @JsonProperty("targetType") String targetType,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("label") String label,
            @JsonProperty("text") String text,
            @JsonProperty("embeddingModel") String embeddingModel,
            @JsonProperty("dimension") Integer dimension,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("createdAt") Instant createdAt
    ) {
        this.vectorItemId = vectorItemId;
        this.targetType = targetType;
        this.sourceId = sourceId;
        this.label = label;
        this.text = text;
        this.embeddingModel = embeddingModel;
        this.dimension = dimension;
        this.metadata = metadata;
        this.createdAt = createdAt;
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

    public String text() {
        return text;
    }

    public String getText() {
        return text;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer dimension() {
        return dimension;
    }

    public Integer getDimension() {
        return dimension;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}