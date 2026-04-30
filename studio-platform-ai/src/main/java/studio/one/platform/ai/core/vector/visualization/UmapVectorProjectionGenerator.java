package studio.one.platform.ai.core.vector.visualization;

/**
 * Dependency-free UMAP-style projection for management visualization.
 * <p>
 * The implementation starts from PCA coordinates and preserves local neighbors
 * with deterministic attraction/repulsion steps so projection jobs remain
 * server-side and repeatable without a native numerical dependency.
 */
public class UmapVectorProjectionGenerator extends NeighborVectorProjectionGenerator {

    public UmapVectorProjectionGenerator() {
        super(12, 80, 0.035d, 0.0008d, 0.12d);
    }

    @Override
    public ProjectionAlgorithm algorithm() {
        return ProjectionAlgorithm.UMAP;
    }
}
