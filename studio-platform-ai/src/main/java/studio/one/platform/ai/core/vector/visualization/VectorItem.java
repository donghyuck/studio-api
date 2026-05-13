package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class VectorItem {

    private final String vectorItemId;
    private final String targetType;
    private final String sourceId;
    private final String label;
    private final String contentText;
    private final List<Double> embedding;
    private final String embeddingModel;
    private final Integer embeddingDimension;
    private final Map<String, Object> metadata;
    private final Instant createdAt;

    public VectorItem(
            String vectorItemId,
            String targetType,
            String sourceId,
            String label,
            String contentText,
            List<Double> embedding,
            String embeddingModel,
            Integer embeddingDimension,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
                embedding = embedding == null ? List.of() : List.copyOf(embedding);
        
        this.vectorItemId = vectorItemId;
        this.targetType = targetType;
        this.sourceId = sourceId;
        this.label = label;
        this.contentText = contentText;
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.embeddingDimension = embeddingDimension;
        this.metadata = metadata;
        this.createdAt = createdAt;
    }

    public String vectorItemId() {
        return vectorItemId;
    }

    public String targetType() {
        return targetType;
    }

    public String sourceId() {
        return sourceId;
    }

    public String label() {
        return label;
    }

    public String contentText() {
        return contentText;
    }

    public List<Double> embedding() {
        return embedding;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    public Integer embeddingDimension() {
        return embeddingDimension;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VectorItem)) {
            return false;
        }
        VectorItem that = (VectorItem) o;
        return java.util.Objects.equals(vectorItemId, that.vectorItemId)
                && java.util.Objects.equals(targetType, that.targetType)
                && java.util.Objects.equals(sourceId, that.sourceId)
                && java.util.Objects.equals(label, that.label)
                && java.util.Objects.equals(contentText, that.contentText)
                && java.util.Objects.equals(embedding, that.embedding)
                && java.util.Objects.equals(embeddingModel, that.embeddingModel)
                && java.util.Objects.equals(embeddingDimension, that.embeddingDimension)
                && java.util.Objects.equals(metadata, that.metadata)
                && java.util.Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(vectorItemId, targetType, sourceId, label, contentText, embedding, embeddingModel, embeddingDimension, metadata, createdAt);
    }

    @Override
    public String toString() {
        return "VectorItem[" +
                "vectorItemId=" + vectorItemId + ", " +
                "targetType=" + targetType + ", " +
                "sourceId=" + sourceId + ", " +
                "label=" + label + ", " +
                "contentText=" + contentText + ", " +
                "embedding=" + embedding + ", " +
                "embeddingModel=" + embeddingModel + ", " +
                "embeddingDimension=" + embeddingDimension + ", " +
                "metadata=" + metadata + ", " +
                "createdAt=" + createdAt +
                "]";
    }
}
