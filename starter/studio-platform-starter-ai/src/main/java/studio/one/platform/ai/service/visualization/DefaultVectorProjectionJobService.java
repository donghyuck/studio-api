package studio.one.platform.ai.service.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;

@Slf4j
public class DefaultVectorProjectionJobService implements VectorProjectionJobService {

    private static final int MAX_EMBEDDING_DIMENSIONS = 2_048;

    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;
    private final ExistingVectorItemRepository itemRepository;
    private final List<VectorProjectionGenerator> generators;

    public DefaultVectorProjectionJobService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            List<VectorProjectionGenerator> generators) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.generators = List.copyOf(generators);
    }

    @Override
    public void run(String projectionId) {
        projectionRepository.updateStatus(projectionId, ProjectionStatus.PROCESSING, null, null);
        try {
            VectorProjection projection = projectionRepository.findById(projectionId)
                    .orElseThrow(() -> new IllegalStateException("Projection not found: " + projectionId));
            VectorProjectionGenerator generator = generatorFor(projection);
            List<VectorItem> items = itemRepository.findItems(projection.targetTypes(), projection.filters());
            if (items.size() > ExistingVectorItemRepository.DEFAULT_MAX_PROJECTION_ITEMS) {
                throw new IllegalStateException("Projection scope is too large. Limit targetTypes or filters.");
            }
            if (items.stream().anyMatch(item -> item.embedding().size() > MAX_EMBEDDING_DIMENSIONS)) {
                throw new IllegalStateException("Projection embedding dimension is too large");
            }
            List<VectorProjectionPoint> points = generator.generate(projectionId, items, Instant.now());
            if (items.isEmpty() || points.isEmpty()) {
                throw new IllegalStateException("No vector items with embeddings were found");
            }
            pointRepository.deleteByProjectionId(projectionId);
            pointRepository.saveAll(points);
            projectionRepository.markCompleted(projectionId, points.size(), Instant.now());
        } catch (Exception ex) {
            log.warn("Vector projection job failed. projectionId={}", projectionId, ex);
            projectionRepository.updateStatus(
                    projectionId,
                    ProjectionStatus.FAILED,
                    ex.getMessage(),
                    Instant.now());
        }
    }

    private VectorProjectionGenerator generatorFor(VectorProjection projection) {
        return generators.stream()
                .filter(generator -> generator.algorithm() == projection.algorithm())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UNSUPPORTED_PROJECTION_ALGORITHM"));
    }
}
