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
        ProjectionMode mode,
        long totalCount,
        int projectedCount,
        boolean sampled,
        Integer sampleSize,
        ProjectionSamplingStrategy samplingStrategy,
        int maxAllowed,
        int itemCount,
        String errorCode,
        String errorMessage,
        String createdBy,
        Instant createdAt,
        Instant completedAt) {

    public VectorProjection {
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        mode = mode == null ? ProjectionMode.DETAIL : mode;
        samplingStrategy = samplingStrategy == null
                ? ProjectionSamplingStrategy.STRATIFIED
                : samplingStrategy;
    }

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
            Instant completedAt) {
        this(projectionId, name, algorithm, status, targetTypes, filters,
                ProjectionMode.DETAIL, itemCount, itemCount, false, null,
                ProjectionSamplingStrategy.STRATIFIED,
                ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS,
                itemCount, null, errorMessage, createdBy, createdAt, completedAt);
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
                ProjectionMode.DETAIL,
                0,
                0,
                false,
                null,
                ProjectionSamplingStrategy.STRATIFIED,
                ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS,
                0,
                null,
                null,
                createdBy,
                createdAt,
                null);
    }

    public static VectorProjection requested(
            String projectionId,
            String name,
            ProjectionAlgorithm algorithm,
            VectorProjectionScope scope,
            ProjectionMode mode,
            long totalCount,
            int projectedCount,
            boolean sampled,
            Integer sampleSize,
            ProjectionSamplingStrategy samplingStrategy,
            int maxAllowed,
            String createdBy,
            Instant createdAt) {
        return new VectorProjection(
                projectionId, name, algorithm, ProjectionStatus.REQUESTED,
                scope.targetTypes(), scope.filters(), mode, totalCount, projectedCount,
                sampled, sampleSize, samplingStrategy, maxAllowed, 0,
                null, null, createdBy, createdAt, null);
    }
}
