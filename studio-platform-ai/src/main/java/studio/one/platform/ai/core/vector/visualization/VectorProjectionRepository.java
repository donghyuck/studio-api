package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VectorProjectionRepository {

    void save(VectorProjection projection);

    Optional<VectorProjection> findById(String projectionId);

    List<VectorProjection> findAll(int limit, int offset);

    default void deleteById(String projectionId) {
        throw new UnsupportedOperationException("Projection deletion is not supported");
    }

    void updateStatus(String projectionId, ProjectionStatus status, String errorMessage, Instant completedAt);

    default void updateStatus(
            String projectionId,
            ProjectionStatus status,
            String errorCode,
            String errorMessage,
            Instant completedAt) {
        updateStatus(projectionId, status, errorMessage, completedAt);
    }

    void markCompleted(String projectionId, int itemCount, List<String> targetTypes, Instant completedAt);

    default int markStaleProcessingFailed(
            Instant cutoff,
            String errorCode,
            String errorMessage,
            Instant completedAt) {
        return 0;
    }
}
