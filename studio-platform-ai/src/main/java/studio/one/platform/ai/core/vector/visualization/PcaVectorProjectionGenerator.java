package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;

/**
 * Dependency-free PCA projection for management visualization.
 */
public class PcaVectorProjectionGenerator implements VectorProjectionGenerator {

    @Override
    public ProjectionAlgorithm algorithm() {
        return ProjectionAlgorithm.PCA;
    }

    @Override
    public List<VectorProjectionPoint> generate(String projectionId, List<VectorItem> items, Instant createdAt) {
        List<VectorItem> usable = ProjectionCoordinateSupport.usableItems(items);
        if (usable.isEmpty()) {
            return List.of();
        }
        List<double[]> coordinates = ProjectionCoordinateSupport.pcaCoordinates(usable);
        return ProjectionCoordinateSupport.points(projectionId, usable, coordinates, createdAt);
    }
}
