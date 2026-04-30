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
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;

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
                                .map(source -> new VectorProjectionPoint(projectionId, source.vectorItemId(), 0.1, 0.2, null, 0, createdAt))
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

    private static VectorItem item(String id) {
        return new VectorItem(id, "COURSE_CHUNK", "course-1", "label", "text", List.of(0.1, 0.2), "model", 2, Map.of(), Instant.now());
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
                        .map(source -> new VectorProjectionPoint(projectionId, source.vectorItemId(), x, y, null, 0, createdAt))
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
        public void updateStatus(String projectionId, ProjectionStatus status, String errorMessage, Instant completedAt) {
            VectorProjection current = projections.get(projectionId);
            projections.put(projectionId, new VectorProjection(
                    current.projectionId(),
                    current.name(),
                    current.algorithm(),
                    status,
                    current.targetTypes(),
                    current.filters(),
                    current.itemCount(),
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
                    itemCount,
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

        private FakeItemRepository(List<VectorItem> items) {
            this.items = items;
        }

        @Override
        public List<VectorItem> findItems(List<String> targetTypes, Map<String, Object> filters) {
            return items;
        }

        @Override
        public Optional<VectorItem> findByVectorItemId(String vectorItemId) {
            return items.stream().filter(item -> item.vectorItemId().equals(vectorItemId)).findFirst();
        }

        @Override
        public List<VectorItem> findByVectorItemIds(Collection<String> vectorItemIds) {
            return items.stream().filter(item -> vectorItemIds.contains(item.vectorItemId())).toList();
        }
    }
}
