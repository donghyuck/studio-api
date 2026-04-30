package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VectorProjection(
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
        Instant completedAt) {

    public VectorProjection {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
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
