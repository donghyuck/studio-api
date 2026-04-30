package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VectorProjectionRepository {

    void save(VectorProjection projection);

    Optional<VectorProjection> findById(String projectionId);

    List<VectorProjection> findAll(int limit, int offset);

    void updateStatus(String projectionId, ProjectionStatus status, String errorMessage, Instant completedAt);

    void markCompleted(String projectionId, int itemCount, Instant completedAt);
}
