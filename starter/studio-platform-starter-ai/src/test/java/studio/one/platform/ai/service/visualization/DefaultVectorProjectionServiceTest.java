package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.PcaVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionMode;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.ProjectionVector;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

class DefaultVectorProjectionServiceTest {

    @Test
    void createStoresRequestedProjectionAndRunsJob() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")));
        DefaultVectorProjectionJobService job = new DefaultVectorProjectionJobService(
                projections,
                points,
                items,
                List.of(new VectorProjectionGenerator() {
                    @Override
                    public ProjectionAlgorithm algorithm() {
                        return ProjectionAlgorithm.PCA;
                    }

                    @Override
                    public List<VectorProjectionPoint> generate(String projectionId, List<VectorItem> sourceItems, Instant createdAt) {
                        return sourceItems.stream()
                                .map(source -> point(projectionId, source, 0.1, 0.2, createdAt))
                                .toList();
                    }
                }));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                points,
                items,
                job,
                Runnable::run);

        VectorProjection projection = service.create(new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.PCA,
                List.of("COURSE_CHUNK"),
                Map.of(),
                "tester"));

        assertThat(projection.status()).isEqualTo(ProjectionStatus.REQUESTED);
        VectorProjection saved = projections.findById(projection.projectionId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(ProjectionStatus.COMPLETED);
        assertThat(saved.itemCount()).isEqualTo(1);
        assertThat(saved.targetTypes()).containsExactly("COURSE_CHUNK");
        assertThat(points.points).hasSize(1);
        assertThat(items.lastStrategy).isEqualTo(ProjectionSamplingStrategy.STRATIFIED);
        assertThat(items.lastLimit).isEqualTo(1);
    }

    @Test
    void createRunsRequestedNonPcaGenerator() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")));
        DefaultVectorProjectionJobService job = new DefaultVectorProjectionJobService(
                projections,
                points,
                items,
                List.of(generator(ProjectionAlgorithm.UMAP)));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                points,
                items,
                job,
                Runnable::run);

        VectorProjection projection = service.create(new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.UMAP,
                List.of("COURSE_CHUNK"),
                Map.of(),
                "tester"));

        assertThat(projection.algorithm()).isEqualTo(ProjectionAlgorithm.UMAP);
        VectorProjection saved = projections.findById(projection.projectionId()).orElseThrow();
        assertThat(saved.status()).isEqualTo(ProjectionStatus.COMPLETED);
        assertThat(saved.algorithm()).isEqualTo(ProjectionAlgorithm.UMAP);
        assertThat(points.points).singleElement()
                .extracting(VectorProjectionPoint::projectionId)
                .isEqualTo(projection.projectionId());
    }

    @Test
    void createWithDefaultPcaStoresPointDisplaySnapshot() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        FakeItemRepository items = new FakeItemRepository(List.of(item(
                "chunk-1",
                Map.of(
                        "_documentChunkId", 101L,
                        "sourceName", "Guide",
                        "chunkId", "chunk-1"))));
        DefaultVectorProjectionJobService job = new DefaultVectorProjectionJobService(
                projections,
                points,
                items,
                List.of(new PcaVectorProjectionGenerator()));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                points,
                items,
                job,
                Runnable::run);

        service.create(new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.PCA,
                List.of("COURSE_CHUNK"),
                Map.of(),
                "tester"));

        assertThat(points.points).singleElement()
                .satisfies(point -> {
                    assertThat(point.documentChunkId()).isEqualTo(101L);
                    assertThat(point.targetType()).isEqualTo("COURSE_CHUNK");
                    assertThat(point.sourceId()).isEqualTo("course-1");
                    assertThat(point.label()).isEqualTo("label");
                    assertThat(point.metadataPreview()).containsEntry("sourceName", "Guide");
                });
    }

    @Test
    void createPrefersCustomGeneratorWhenDefaultGeneratorHasSameAlgorithm() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")));
        DefaultVectorProjectionJobService job = new DefaultVectorProjectionJobService(
                projections,
                points,
                items,
                List.of(new PcaVectorProjectionGenerator(), generator(ProjectionAlgorithm.PCA, 0.7, 0.8)));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                points,
                items,
                job,
                Runnable::run);

        VectorProjection projection = service.create(new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.PCA,
                List.of("COURSE_CHUNK"),
                Map.of(),
                "tester"));

        assertThat(projections.findById(projection.projectionId()).orElseThrow().status())
                .isEqualTo(ProjectionStatus.COMPLETED);
        assertThat(points.points).singleElement()
                .satisfies(point -> {
                    assertThat(point.x()).isEqualTo(0.7);
                    assertThat(point.y()).isEqualTo(0.8);
                });
    }

    @Test
    void pointsRejectsProjectionThatIsNotCompleted() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        projections.save(VectorProjection.requested(
                "proj-1",
                "map",
                ProjectionAlgorithm.PCA,
                List.of(),
                Map.of(),
                null,
                Instant.now()));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                new FakePointRepository(),
                new FakeItemRepository(List.of()),
                projectionId -> {},
                Runnable::run);

        assertThatThrownBy(() -> service.points("proj-1", null, null, null, 2000, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PROJECTION_NOT_READY");
    }

    @Test
    void deleteRemovesProjectionAndItsPoints() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        VectorProjection projection = completedProjection("proj-delete");
        projections.save(projection);
        points.saveAll(List.of(point(projection.projectionId(), item("chunk-1"), 0.1, 0.2, Instant.now())));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections, points, new FakeItemRepository(List.of()), projectionId -> {}, Runnable::run);

        service.delete(projection.projectionId());

        assertThat(projections.findById(projection.projectionId())).isEmpty();
        assertThat(points.points).isEmpty();
    }

    @Test
    void deleteRejectsProjectionThatIsStillRunning() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        projections.save(VectorProjection.requested(
                "proj-running", "map", ProjectionAlgorithm.PCA, List.of(), Map.of(), null, Instant.now()));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections, new FakePointRepository(), new FakeItemRepository(List.of()),
                projectionId -> {}, Runnable::run);

        assertThatThrownBy(() -> service.delete("proj-running"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PROJECTION_DELETE_IN_PROGRESS");
        assertThat(projections.findById("proj-running")).isPresent();
    }

    @Test
    void createMarksProjectionFailedWhenJobCannotBeQueued() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        FakePointRepository points = new FakePointRepository();
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")));
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                points,
                items,
                projectionId -> {},
                task -> {
                    throw new RejectedExecutionException("queue full");
                });

        assertThatThrownBy(() -> service.create(new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.PCA,
                List.of("COURSE_CHUNK"),
                Map.of(),
                "tester")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PROJECTION_JOB_QUEUE_UNAVAILABLE");
        assertThat(projections.projections.values()).singleElement()
                .extracting(VectorProjection::status)
                .isEqualTo(ProjectionStatus.FAILED);
    }

    @Test
    void createRejectsTooLongNameBeforePersistence() {
        FakeProjectionRepository projections = new FakeProjectionRepository();
        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projections,
                new FakePointRepository(),
                new FakeItemRepository(List.of()),
                projectionId -> {},
                Runnable::run);

        assertThatThrownBy(() -> service.create(new VectorProjectionCreateCommand(
                "x".repeat(201),
                ProjectionAlgorithm.PCA,
                List.of(),
                Map.of(),
                "tester")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("name must be at most 200 characters");
        assertThat(projections.projections).isEmpty();
    }

    @Test
    void detailRequiresScopeBeforeCounting() {
        FakeItemRepository items = new FakeItemRepository(List.of());
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        assertThatThrownBy(() -> service.create(command(
                ProjectionMode.DETAIL, List.of(), Map.of(), null, ProjectionSamplingStrategy.STRATIFIED)))
                .isInstanceOf(VectorProjectionException.class)
                .satisfies(error -> assertThat(((VectorProjectionException) error).code())
                        .isEqualTo("PROJECTION_SCOPE_REQUIRED"));
        assertThat(items.countCalls).isZero();
    }

    @Test
    void overviewAutomaticallySamplesLargeScope() {
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")), 7_534);
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        VectorProjection projection = service.create(command(
                ProjectionMode.OVERVIEW, List.of(), Map.of(), null, ProjectionSamplingStrategy.STRATIFIED));

        assertThat(projection.totalCount()).isEqualTo(7_534);
        assertThat(projection.projectedCount()).isEqualTo(1_000);
        assertThat(projection.sampled()).isTrue();
        assertThat(projection.sampleSize()).isEqualTo(1_000);
    }

    @Test
    void detailRejectsLargeScopeWithoutSampleSize() {
        FakeItemRepository items = new FakeItemRepository(List.of(), 2_133);
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        assertThatThrownBy(() -> service.create(command(
                ProjectionMode.DETAIL,
                List.of(),
                Map.of("attachmentId", 14),
                null,
                ProjectionSamplingStrategy.STRATIFIED)))
                .isInstanceOf(VectorProjectionException.class)
                .satisfies(error -> {
                    VectorProjectionException projectionError = (VectorProjectionException) error;
                    assertThat(projectionError.code()).isEqualTo("PROJECTION_LIMIT_EXCEEDED");
                    assertThat(projectionError.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(projectionError.totalCount()).isEqualTo(2_133);
                });
    }

    @Test
    void detailUsesRequestedSampleAndNormalizedChunkRange() {
        FakeItemRepository items = new FakeItemRepository(List.of(item("chunk-1")), 2_133);
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        VectorProjection projection = service.create(command(
                ProjectionMode.DETAIL,
                List.of(),
                Map.of("attachmentId", 14, "chunkIndexFrom", "0", "chunkIndexTo", 999),
                1_000,
                ProjectionSamplingStrategy.HEAD));

        assertThat(projection.projectedCount()).isEqualTo(1_000);
        assertThat(projection.samplingStrategy()).isEqualTo(ProjectionSamplingStrategy.HEAD);
        assertThat(items.lastScope.filters())
                .containsEntry("attachmentId", 14)
                .containsEntry("chunkIndexFrom", 0)
                .containsEntry("chunkIndexTo", 999);
    }

    @Test
    void estimateUsesSameScopeAndReturnsRecommendation() {
        FakeItemRepository items = new FakeItemRepository(List.of(), 7_534);
        DefaultVectorProjectionService service = service(items, 1_000, 800);

        VectorProjectionEstimate estimate = service.estimate(command(
                ProjectionMode.OVERVIEW,
                List.of("attachment"),
                Map.of("objectId", 17),
                null,
                ProjectionSamplingStrategy.RANDOM));

        assertThat(estimate.totalCount()).isEqualTo(7_534);
        assertThat(estimate.maxAllowed()).isEqualTo(1_000);
        assertThat(estimate.exceedsLimit()).isTrue();
        assertThat(estimate.recommendedSampleSize()).isEqualTo(800);
        assertThat(items.lastScope.targetTypes()).containsExactly("attachment");
        assertThat(items.lastScope.filters()).containsEntry("objectId", 17);
    }

    @Test
    void invalidChunkRangeIsRejectedBeforeCounting() {
        FakeItemRepository items = new FakeItemRepository(List.of());
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        assertThatThrownBy(() -> service.create(command(
                ProjectionMode.DETAIL,
                List.of(),
                Map.of("attachmentId", 14, "chunkIndexFrom", 100, "chunkIndexTo", 10),
                null,
                ProjectionSamplingStrategy.HEAD)))
                .isInstanceOf(VectorProjectionException.class)
                .satisfies(error -> assertThat(((VectorProjectionException) error).code())
                        .isEqualTo("PROJECTION_CHUNK_RANGE_INVALID"));
        assertThat(items.countCalls).isZero();
    }

    @Test
    void invalidMetadataFilterKeyIsRejectedBeforeJdbcAccess() {
        FakeItemRepository items = new FakeItemRepository(List.of());
        DefaultVectorProjectionService service = service(items, 1_000, 1_000);

        assertThatThrownBy(() -> service.create(command(
                ProjectionMode.DETAIL,
                List.of(),
                Map.of("unsafe key", "value"),
                null,
                ProjectionSamplingStrategy.HEAD)))
                .isInstanceOf(VectorProjectionException.class)
                .satisfies(error -> assertThat(((VectorProjectionException) error).code())
                        .isEqualTo("PROJECTION_FILTER_INVALID"));
        assertThat(items.countCalls).isZero();
    }

    private DefaultVectorProjectionService service(FakeItemRepository items, int maxItems, int defaultSampleSize) {
        return new DefaultVectorProjectionService(
                new FakeProjectionRepository(),
                new FakePointRepository(),
                items,
                projectionId -> {},
                Runnable::run,
                maxItems,
                defaultSampleSize);
    }

    private VectorProjectionCreateCommand command(
            ProjectionMode mode,
            List<String> targetTypes,
            Map<String, Object> filters,
            Integer sampleSize,
            ProjectionSamplingStrategy strategy) {
        return new VectorProjectionCreateCommand(
                "map",
                ProjectionAlgorithm.PCA,
                targetTypes,
                filters,
                mode,
                sampleSize,
                strategy,
                "tester");
    }

    private static VectorItem item(String id) {
        return new VectorItem(id, "COURSE_CHUNK", "course-1", "label", "text", List.of(0.1, 0.2), "model", 2, Map.of(), Instant.now());
    }

    private static VectorItem item(String id, Map<String, Object> metadata) {
        return new VectorItem(id, "COURSE_CHUNK", "course-1", "label", "text", List.of(0.1, 0.2), "model", 2, metadata, Instant.now());
    }

    private static VectorProjectionPoint point(
            String projectionId,
            VectorItem source,
            double x,
            double y,
            Instant createdAt) {
        return new VectorProjectionPoint(
                projectionId,
                source.vectorItemId(),
                null,
                source.targetType(),
                source.sourceId(),
                source.label(),
                source.metadata(),
                x,
                y,
                null,
                0,
                createdAt);
    }

    private static VectorProjection completedProjection(String projectionId) {
        Instant now = Instant.now();
        return new VectorProjection(
                projectionId, "map", ProjectionAlgorithm.PCA, ProjectionStatus.COMPLETED,
                List.of("COURSE_CHUNK"), Map.of(), ProjectionMode.DETAIL,
                1, 1, false, null, ProjectionSamplingStrategy.STRATIFIED,
                1_000, 1, null, null, "tester", now, now);
    }

    private static VectorProjectionGenerator generator(ProjectionAlgorithm algorithm) {
        return generator(algorithm, 0.1, 0.2);
    }

    private static VectorProjectionGenerator generator(ProjectionAlgorithm algorithm, double x, double y) {
        return new VectorProjectionGenerator() {
            @Override
            public ProjectionAlgorithm algorithm() {
                return algorithm;
            }

            @Override
            public List<VectorProjectionPoint> generate(String projectionId, List<VectorItem> sourceItems, Instant createdAt) {
                return sourceItems.stream()
                        .map(source -> point(projectionId, source, x, y, createdAt))
                        .toList();
            }
        };
    }

    private static final class FakeProjectionRepository implements VectorProjectionRepository {
        private final Map<String, VectorProjection> projections = new LinkedHashMap<>();

        @Override
        public void save(VectorProjection projection) {
            projections.put(projection.projectionId(), projection);
        }

        @Override
        public Optional<VectorProjection> findById(String projectionId) {
            return Optional.ofNullable(projections.get(projectionId));
        }

        @Override
        public List<VectorProjection> findAll(int limit, int offset) {
            return projections.values().stream().skip(offset).limit(limit).toList();
        }

        @Override
        public void deleteById(String projectionId) {
            projections.remove(projectionId);
        }

        @Override
        public void updateStatus(String projectionId, ProjectionStatus status, String errorMessage, Instant completedAt) {
            updateStatus(projectionId, status, null, errorMessage, completedAt);
        }

        @Override
        public void updateStatus(
                String projectionId,
                ProjectionStatus status,
                String errorCode,
                String errorMessage,
                Instant completedAt) {
            VectorProjection current = projections.get(projectionId);
            projections.put(projectionId, new VectorProjection(
                    current.projectionId(),
                    current.name(),
                    current.algorithm(),
                    status,
                    current.targetTypes(),
                    current.filters(),
                    current.mode(),
                    current.totalCount(),
                    current.projectedCount(),
                    current.sampled(),
                    current.sampleSize(),
                    current.samplingStrategy(),
                    current.maxAllowed(),
                    current.itemCount(),
                    errorCode,
                    errorMessage,
                    current.createdBy(),
                    current.createdAt(),
                    completedAt));
        }

        @Override
        public void markCompleted(String projectionId, int itemCount, List<String> targetTypes, Instant completedAt) {
            VectorProjection current = projections.get(projectionId);
            projections.put(projectionId, new VectorProjection(
                    current.projectionId(),
                    current.name(),
                    current.algorithm(),
                    ProjectionStatus.COMPLETED,
                    targetTypes,
                    current.filters(),
                    current.mode(),
                    current.totalCount(),
                    itemCount,
                    current.totalCount() > itemCount,
                    current.sampleSize(),
                    current.samplingStrategy(),
                    current.maxAllowed(),
                    itemCount,
                    null,
                    null,
                    current.createdBy(),
                    current.createdAt(),
                    completedAt));
        }
    }

    private static final class FakePointRepository implements VectorProjectionPointRepository {
        private final List<VectorProjectionPoint> points = new ArrayList<>();

        @Override
        public void deleteByProjectionId(String projectionId) {
            points.removeIf(point -> point.projectionId().equals(projectionId));
        }

        @Override
        public void saveAll(List<VectorProjectionPoint> points) {
            this.points.addAll(points);
        }

        @Override
        public ProjectionPointPage findPage(String projectionId, String targetType, String clusterId, String keyword, int limit, int offset) {
            return new ProjectionPointPage(points.size(), List.of());
        }

        @Override
        public List<ProjectionPointView> findByVectorItemIds(String projectionId, Collection<String> vectorItemIds) {
            return List.of();
        }

        @Override
        public Optional<ProjectionPointView> findByVectorItemId(String projectionId, String vectorItemId) {
            return Optional.empty();
        }
    }

    private static final class FakeItemRepository implements ExistingVectorItemRepository {
        private final List<VectorItem> items;
        private final long totalCount;
        private int countCalls;
        private VectorProjectionScope lastScope;
        private ProjectionSamplingStrategy lastStrategy;
        private int lastLimit;

        private FakeItemRepository(List<VectorItem> items) {
            this(items, items.size());
        }

        private FakeItemRepository(List<VectorItem> items, long totalCount) {
            this.items = items;
            this.totalCount = totalCount;
        }

        @Override
        public List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters) {
            return items;
        }

        @Override
        public long count(VectorProjectionScope scope) {
            countCalls++;
            lastScope = scope;
            return totalCount;
        }

        @Override
        public List<VectorItem> findItems(
                VectorProjectionScope scope,
                ProjectionSamplingStrategy samplingStrategy,
                int limit) {
            lastScope = scope;
            lastStrategy = samplingStrategy;
            lastLimit = limit;
            return items.stream().limit(limit).toList();
        }

        @Override
        public List<ProjectionVector> findProjectionVectors(
                VectorProjectionScope scope,
                ProjectionSamplingStrategy samplingStrategy,
                int limit) {
            lastScope = scope;
            lastStrategy = samplingStrategy;
            lastLimit = limit;
            return items.stream()
                    .limit(limit)
                    .map(item -> new ProjectionVector(
                            item.vectorItemId(),
                            documentChunkId(item.metadata()),
                            item.targetType(),
                            item.sourceId(),
                            item.label(),
                            item.metadata(),
                            item.embedding().stream().mapToDouble(Double::doubleValue).toArray(),
                            item.embeddingModel(),
                            item.embeddingDimension(),
                            item.createdAt()))
                    .toList();
        }

        @Override
        public Optional<VectorItem> findByVectorItemId(String vectorItemId) {
            return items.stream().filter(item -> item.vectorItemId().equals(vectorItemId)).findFirst();
        }

        private Long documentChunkId(Map<String, Object> metadata) {
            Object value = metadata.get("_documentChunkId");
            if (value instanceof Number number) {
                return number.longValue();
            }
            return null;
        }

        @Override
        public List<VectorItem> findByVectorItemIds(Collection<String> vectorItemIds) {
            return items.stream().filter(item -> vectorItemIds.contains(item.vectorItemId())).toList();
        }
    }
}
