package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPoint;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;

class DefaultVectorSearchVisualizationServiceTest {

    @Test
    void searchReturnsQueryCentroidAndMatchingProjectionPoints() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository points = new FakePointRepository(List.of(
                new ProjectionPointView("chunk-1", "COURSE_CHUNK", "course-1", "Java", 0.2, 0.4, null, Map.of()),
                new ProjectionPointView("chunk-2", "COURSE_CHUNK", "course-2", "Spring", 0.6, 0.8, null, Map.of())));
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(
                new VectorSearchHit("chunk-1", "doc-1", "chunk-1", null, null, 0.9, null, null, null, null, null,
                        Map.of("chunkId", "chunk-1")),
                new VectorSearchHit("chunk-2", "doc-2", "chunk-2", null, null, 0.8, null, null, null, null, null,
                        Map.of("chunkId", "chunk-2"))), 1L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projections,
                points);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null));

        assertThat(result.results()).hasSize(2);
        assertThat(result.query().x()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result.query().y()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void searchReturnsNullQueryPointWhenNoProjectionPointMatches() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(
                new VectorSearchHit("missing", "doc-1", "missing", null, null, 0.9, null, null, null, null, null,
                        Map.of("chunkId", "missing"))), 1L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projections,
                new FakePointRepository(List.of()));

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null));

        assertThat(result.results()).isEmpty();
        assertThat(result.query().x()).isNull();
        assertThat(result.query().y()).isNull();
    }

    @Test
    void searchUsesRowVectorItemIdWhenChunkIdIsAbsent() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository points = new FakePointRepository(List.of(
                new ProjectionPointView("row-7", "COURSE_CHUNK", "course-1", "Java", 0.2, 0.4, null, Map.of())));
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(
                new VectorSearchHit("doc-1", "doc-1", "doc-1", null, null, 0.9, null, null, null, null, null,
                        Map.of("_vectorRowId", "row-7", "documentId", "doc-1"))), 1L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projections,
                points);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null));

        assertThat(result.results()).singleElement()
                .extracting(VectorSearchVisualizationResult.ResultPoint::vectorItemId)
                .isEqualTo("row-7");
    }

    private VectorProjection projection() {
        return new VectorProjection(
                "proj-1",
                "map",
                ProjectionAlgorithm.PCA,
                ProjectionStatus.COMPLETED,
                List.of(),
                Map.of(),
                2,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private static final class FakePointRepository implements VectorProjectionPointRepository {
        private final List<ProjectionPointView> points;

        private FakePointRepository(List<ProjectionPointView> points) {
            this.points = points;
        }

        @Override
        public void deleteByProjectionId(String projectionId) {
        }

        @Override
        public void saveAll(List<VectorProjectionPoint> points) {
        }

        @Override
        public ProjectionPointPage findPage(String projectionId, String targetType, String clusterId, String keyword, int limit, int offset) {
            return new ProjectionPointPage(points.size(), points);
        }

        @Override
        public List<ProjectionPointView> findByVectorItemIds(String projectionId, Collection<String> vectorItemIds) {
            return points.stream().filter(point -> vectorItemIds.contains(point.vectorItemId())).toList();
        }

        @Override
        public Optional<ProjectionPointView> findByVectorItemId(String projectionId, String vectorItemId) {
            return points.stream().filter(point -> point.vectorItemId().equals(vectorItemId)).findFirst();
        }
    }
}
