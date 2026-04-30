package studio.one.platform.ai.core.vector.visualization;

/**
 * Dependency-free t-SNE-style projection for management visualization.
 * <p>
 * This generator uses a deterministic neighbor-preserving refinement over PCA
 * initialization. It is intentionally bounded for management scatter plots and
 * can be replaced by a library-backed implementation through
 * {@link VectorProjectionGenerator}.
 */
public class TsneVectorProjectionGenerator extends NeighborVectorProjectionGenerator {

    public TsneVectorProjectionGenerator() {
        super(30, 120, 0.025d, 0.0015d, 0.08d);
    }

    @Override
    public ProjectionAlgorithm algorithm() {
        return ProjectionAlgorithm.TSNE;
    }
}
