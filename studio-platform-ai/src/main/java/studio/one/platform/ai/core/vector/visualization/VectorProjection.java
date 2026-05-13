package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class VectorProjection {

    private final String projectionId;
    private final String name;
    private final ProjectionAlgorithm algorithm;
    private final ProjectionStatus status;
    private final List<String> targetTypes;
    private final Map<String, Object> filters;
    private final int itemCount;
    private final String errorMessage;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant completedAt;

    public VectorProjection(
            String projectionId,
            String name,
            ProjectionAlgorithm algorithm,
            ProjectionStatus status,
            List<String> targetTypes,
            Map<String, Object> filters,
            int itemCount,
            String errorMessage,
            String createdBy,
            Instant createdAt,
            Instant completedAt
    ) {
                targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
                filters = filters == null ? Map.of() : Map.copyOf(filters);
        
        this.projectionId = projectionId;
        this.name = name;
        this.algorithm = algorithm;
        this.status = status;
        this.targetTypes = targetTypes;
        this.filters = filters;
        this.itemCount = itemCount;
        this.errorMessage = errorMessage;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public String projectionId() {
        return projectionId;
    }

    public String name() {
        return name;
    }

    public ProjectionAlgorithm algorithm() {
        return algorithm;
    }

    public ProjectionStatus status() {
        return status;
    }

    public List<String> targetTypes() {
        return targetTypes;
    }

    public Map<String, Object> filters() {
        return filters;
    }

    public int itemCount() {
        return itemCount;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VectorProjection)) {
            return false;
        }
        VectorProjection that = (VectorProjection) o;
        return java.util.Objects.equals(projectionId, that.projectionId)
                && java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(algorithm, that.algorithm)
                && java.util.Objects.equals(status, that.status)
                && java.util.Objects.equals(targetTypes, that.targetTypes)
                && java.util.Objects.equals(filters, that.filters)
                && itemCount == that.itemCount
                && java.util.Objects.equals(errorMessage, that.errorMessage)
                && java.util.Objects.equals(createdBy, that.createdBy)
                && java.util.Objects.equals(createdAt, that.createdAt)
                && java.util.Objects.equals(completedAt, that.completedAt);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(projectionId, name, algorithm, status, targetTypes, filters, itemCount, errorMessage, createdBy, createdAt, completedAt);
    }

    @Override
    public String toString() {
        return "VectorProjection[" +
                "projectionId=" + projectionId + ", " +
                "name=" + name + ", " +
                "algorithm=" + algorithm + ", " +
                "status=" + status + ", " +
                "targetTypes=" + targetTypes + ", " +
                "filters=" + filters + ", " +
                "itemCount=" + itemCount + ", " +
                "errorMessage=" + errorMessage + ", " +
                "createdBy=" + createdBy + ", " +
                "createdAt=" + createdAt + ", " +
                "completedAt=" + completedAt +
                "]";
    }

    public static VectorProjection requested(
            String projectionId,
            String name,
            ProjectionAlgorithm algorithm,
            List<String> targetTypes,
            Map<String, Object> filters,
            String createdBy,
            Instant createdAt) {
        return new VectorProjection(
                projectionId,
                name,
                algorithm,
                ProjectionStatus.REQUESTED,
                targetTypes,
                filters,
                0,
                null,
                createdBy,
                createdAt,
                null);
    }
}
