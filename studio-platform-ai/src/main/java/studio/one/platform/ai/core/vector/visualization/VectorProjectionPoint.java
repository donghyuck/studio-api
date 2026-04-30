package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;

public record VectorProjectionPoint(
        String projectionId,
        String vectorItemId,
        double x,
        double y,
        String clusterId,
        Integer displayOrder,
        Instant createdAt) {
}
