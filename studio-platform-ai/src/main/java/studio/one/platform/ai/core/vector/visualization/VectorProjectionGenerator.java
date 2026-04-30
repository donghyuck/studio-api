package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;

public interface VectorProjectionGenerator {

    ProjectionAlgorithm algorithm();

    List<VectorProjectionPoint> generate(String projectionId, List<VectorItem> items, Instant createdAt);
}
