package studio.one.platform.ai.service.visualization;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.PcaVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.ProjectionVector;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.TsneVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.UmapVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

@Slf4j
public class DefaultVectorProjectionJobService implements VectorProjectionJobService {

    private static final int MAX_EMBEDDING_DIMENSIONS = 2_048;

    private final VectorProjectionRepository projectionRepository;
    private final VectorProjectionPointRepository pointRepository;
    private final ExistingVectorItemRepository itemRepository;
    private final List<VectorProjectionGenerator> generators;
    private final VectorProjectionNotifier projectionNotifier;

    public DefaultVectorProjectionJobService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            List<VectorProjectionGenerator> generators) {
        this(projectionRepository, pointRepository, itemRepository, generators, VectorProjectionNotifier.NOOP);
    }

    public DefaultVectorProjectionJobService(
            VectorProjectionRepository projectionRepository,
            VectorProjectionPointRepository pointRepository,
            ExistingVectorItemRepository itemRepository,
            List<VectorProjectionGenerator> generators,
            VectorProjectionNotifier projectionNotifier) {
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.pointRepository = Objects.requireNonNull(pointRepository, "pointRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
        this.generators = List.copyOf(generators);
        this.projectionNotifier = projectionNotifier == null ? VectorProjectionNotifier.NOOP : projectionNotifier;
    }

    @Override
    public void run(String projectionId) {
        try {
            projectionRepository.updateStatus(projectionId, ProjectionStatus.PROCESSING, null, null);
            notifyProjection(projectionId);

            VectorProjection projection = projectionRepository.findById(projectionId)
                    .orElseThrow(() -> new IllegalStateException("Projection not found: " + projectionId));
            VectorProjectionGenerator generator = generatorFor(projection);
            ProjectionRunResult result = runProjection(projection, generator);
            List<VectorProjectionPoint> points = result.points();
            if (points.isEmpty()) {
                throw new IllegalStateException("Projection generator returned no points");
            }
            pointRepository.deleteByProjectionId(projectionId);
            pointRepository.saveAll(points);
            projectionRepository.markCompleted(projectionId, points.size(), result.targetTypes(), Instant.now());
            notifyProjection(projectionId);
        } catch (Throwable ex) {
            String errorCode = errorCode(ex);
            projectionRepository.updateStatus(
                    projectionId,
                    ProjectionStatus.FAILED,
                    errorCode,
                    ex.getMessage(),
                    Instant.now());
            notifyProjection(projectionId);
            log.warn("Vector projection job failed: {}", projectionId, ex);
        }
    }

    private ProjectionRunResult runProjection(
            studio.one.platform.ai.core.vector.visualization.VectorProjection projection,
            VectorProjectionGenerator generator) {
        Instant loadStart = Instant.now();
        logProjectionMemory("load.start", projection, 0, 0, loadStart);
        if (generator instanceof PcaVectorProjectionGenerator pcaGenerator) {
            List<ProjectionVector> vectors = itemRepository.findProjectionVectors(
                    new VectorProjectionScope(projection.targetTypes(), projection.filters()),
                    projection.samplingStrategy(),
                    projection.projectedCount());
            if (vectors.isEmpty()) {
                throw new IllegalStateException("No vector items with embeddings were found");
            }
            int dimensions = projectionVectorDimensions(vectors);
            if (dimensions > MAX_EMBEDDING_DIMENSIONS) {
                throw new IllegalStateException("Projection embedding dimension is too large");
            }
            logProjectionMemory("load.end", projection, vectors.size(), dimensions, loadStart);

            Instant projectStart = Instant.now();
            List<VectorProjectionPoint> points = pcaGenerator.generateProjectionVectors(
                    projection.projectionId(),
                    vectors,
                    Instant.now());
            logProjectionMemory("project.end", projection, points.size(), dimensions, projectStart);
            return new ProjectionRunResult(points, actualVectorTargetTypes(vectors));
        }

        List<VectorItem> items = itemRepository.findItems(
                new VectorProjectionScope(projection.targetTypes(), projection.filters()),
                projection.samplingStrategy(),
                projection.projectedCount());
        if (items.isEmpty()) {
            throw new IllegalStateException("No vector items with embeddings were found");
        }
        int dimensions = embeddingDimensions(items);
        if (dimensions > MAX_EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException("Projection embedding dimension is too large");
        }
        logProjectionMemory("load.end", projection, items.size(), dimensions, loadStart);

        Instant projectStart = Instant.now();
        List<VectorProjectionPoint> points = generator.generate(projection.projectionId(), items, Instant.now());
        logProjectionMemory("project.end", projection, points.size(), dimensions, projectStart);
        return new ProjectionRunResult(points, actualTargetTypes(items));
    }

    private void notifyProjection(String projectionId) {
        projectionRepository.findById(projectionId).ifPresent(projectionNotifier::notifyProjection);
    }

    private String errorCode(Throwable ex) {
        if (ex instanceof OutOfMemoryError) {
            return "PROJECTION_JOB_OUT_OF_MEMORY";
        }

        if (ex instanceof IllegalArgumentException
                && "UNSUPPORTED_PROJECTION_ALGORITHM".equals(ex.getMessage())) {
            return "UNSUPPORTED_PROJECTION_ALGORITHM";
        }

        if ("No vector items with embeddings were found".equals(ex.getMessage())) {
            return "PROJECTION_ITEMS_NOT_FOUND";
        }

        if ("Projection embedding dimension is too large".equals(ex.getMessage())) {
            return "PROJECTION_DIMENSION_EXCEEDED";
        }

        return "PROJECTION_JOB_FAILED";
    }

    private VectorProjectionGenerator generatorFor(VectorProjection projection) {
        List<VectorProjectionGenerator> matching = generators.stream()
                .filter(generator -> generator.algorithm() == projection.algorithm())
                .toList();
        return matching.stream()
                .filter(generator -> !isDefaultGenerator(generator))
                .findFirst()
                .or(() -> matching.stream().findFirst())
                .orElseThrow(() -> new IllegalArgumentException("UNSUPPORTED_PROJECTION_ALGORITHM"));
    }

    private boolean isDefaultGenerator(VectorProjectionGenerator generator) {
        return generator instanceof PcaVectorProjectionGenerator
                || generator instanceof UmapVectorProjectionGenerator
                || generator instanceof TsneVectorProjectionGenerator;
    }

    private int embeddingDimensions(List<VectorItem> items) {
        return items.stream()
                .map(VectorItem::embedding)
                .filter(embedding -> embedding != null && !embedding.isEmpty())
                .mapToInt(List::size)
                .max()
                .orElse(0);
    }

    private int projectionVectorDimensions(List<ProjectionVector> vectors) {
        return vectors.stream()
                .map(ProjectionVector::embedding)
                .filter(embedding -> embedding != null && embedding.length > 0)
                .mapToInt(embedding -> embedding.length)
                .max()
                .orElse(0);
    }

    private void logProjectionMemory(
            String phase,
            studio.one.platform.ai.core.vector.visualization.VectorProjection projection,
            int count,
            int dimensions,
            Instant phaseStart) {
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        long elapsedMs = Duration.between(phaseStart, Instant.now()).toMillis();
        log.info(
                "Vector projection {}. projectionId={}, mode={}, algorithm={}, totalCount={}, projectedCount={}, "
                        + "samplingStrategy={}, count={}, dimensions={}, heapUsedMb={}, heapMaxMb={}, elapsedMs={}",
                phase,
                projection.projectionId(),
                projection.mode(),
                projection.algorithm(),
                projection.totalCount(),
                projection.projectedCount(),
                projection.samplingStrategy(),
                count,
                dimensions,
                usedMb,
                maxMb,
                elapsedMs);
    }

    private List<String> actualTargetTypes(List<VectorItem> items) {
        return items.stream()
                .map(VectorItem::targetType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
    private List<String> actualVectorTargetTypes(List<ProjectionVector> vectors) {
        return vectors.stream()
                .map(ProjectionVector::targetType)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private record ProjectionRunResult(List<VectorProjectionPoint> points, List<String> targetTypes) {
    }
}
