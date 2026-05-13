package studio.one.platform.ai.core.vector.visualization;

import java.util.Map;

public final class ProjectionPointView {

    private final String vectorItemId;
    private final String targetType;
    private final String sourceId;
    private final String label;
    private final double x;
    private final double y;
    private final String clusterId;
    private final Map<String, Object> metadata;

    public ProjectionPointView(
            String vectorItemId,
            String targetType,
            String sourceId,
            String label,
            double x,
            double y,
            String clusterId,
            Map<String, Object> metadata
    ) {
                metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        
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

    public String targetType() {
        return targetType;
    }

    public String sourceId() {
        return sourceId;
    }

    public String label() {
        return label;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public String clusterId() {
        return clusterId;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectionPointView)) {
            return false;
        }
        ProjectionPointView that = (ProjectionPointView) o;
        return java.util.Objects.equals(vectorItemId, that.vectorItemId)
                && java.util.Objects.equals(targetType, that.targetType)
                && java.util.Objects.equals(sourceId, that.sourceId)
                && java.util.Objects.equals(label, that.label)
                && Double.compare(that.x, x) == 0
                && Double.compare(that.y, y) == 0
                && java.util.Objects.equals(clusterId, that.clusterId)
                && java.util.Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(vectorItemId, targetType, sourceId, label, x, y, clusterId, metadata);
    }

    @Override
    public String toString() {
        return "ProjectionPointView[" +
                "vectorItemId=" + vectorItemId + ", " +
                "targetType=" + targetType + ", " +
                "sourceId=" + sourceId + ", " +
                "label=" + label + ", " +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "clusterId=" + clusterId + ", " +
                "metadata=" + metadata +
                "]";
    }
}
