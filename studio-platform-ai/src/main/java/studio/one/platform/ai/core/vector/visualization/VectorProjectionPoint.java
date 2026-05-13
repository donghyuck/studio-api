package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;

public final class VectorProjectionPoint {

    private final String projectionId;
    private final String vectorItemId;
    private final double x;
    private final double y;
    private final String clusterId;
    private final Integer displayOrder;
    private final Instant createdAt;

    public VectorProjectionPoint(
            String projectionId,
            String vectorItemId,
            double x,
            double y,
            String clusterId,
            Integer displayOrder,
            Instant createdAt
    ) {
        this.projectionId = projectionId;
        this.vectorItemId = vectorItemId;
        this.x = x;
        this.y = y;
        this.clusterId = clusterId;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    public String projectionId() {
        return projectionId;
    }

    public String vectorItemId() {
        return vectorItemId;
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

    public Integer displayOrder() {
        return displayOrder;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VectorProjectionPoint)) {
            return false;
        }
        VectorProjectionPoint that = (VectorProjectionPoint) o;
        return java.util.Objects.equals(projectionId, that.projectionId)
                && java.util.Objects.equals(vectorItemId, that.vectorItemId)
                && Double.compare(that.x, x) == 0
                && Double.compare(that.y, y) == 0
                && java.util.Objects.equals(clusterId, that.clusterId)
                && java.util.Objects.equals(displayOrder, that.displayOrder)
                && java.util.Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(projectionId, vectorItemId, x, y, clusterId, displayOrder, createdAt);
    }

    @Override
    public String toString() {
        return "VectorProjectionPoint[" +
                "projectionId=" + projectionId + ", " +
                "vectorItemId=" + vectorItemId + ", " +
                "x=" + x + ", " +
                "y=" + y + ", " +
                "clusterId=" + clusterId + ", " +
                "displayOrder=" + displayOrder + ", " +
                "createdAt=" + createdAt +
                "]";
    }
}
