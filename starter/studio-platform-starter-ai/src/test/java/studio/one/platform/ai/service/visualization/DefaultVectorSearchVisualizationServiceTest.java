package studio.one.platform.ai.service.visualization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import studio.one.platform.ai.core.chat.ChatPort;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingRequest;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.VectorDocument;
import studio.one.platform.ai.core.vector.VectorSearchHit;
import studio.one.platform.ai.core.vector.VectorSearchRequest;
import studio.one.platform.ai.core.vector.VectorSearchResult;
import studio.one.platform.ai.core.vector.VectorSearchResults;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
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
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
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
                points,
                items);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null,
                null,
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
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
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
                new FakePointRepository(List.of()),
                items);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null,
                null,
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
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
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
                points,
                items);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null,
                null,
                null));

        assertThat(result.results()).singleElement()
                .extracting(VectorSearchVisualizationResult.ResultPoint::vectorItemId)
                .isEqualTo("row-7");
    }

    @Test
    void searchWithTargetTypesUsesObjectTypeOnlyVectorScope() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository points = new FakePointRepository(List.of(
                new ProjectionPointView("chunk-1", "attachment", "6", "Document", 0.2, 0.4, null, Map.of())));
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(
                new VectorSearchHit("chunk-1", "doc-1", "chunk-1", null, null, 0.9, null, null, null, null, null,
                        Map.of("chunkId", "chunk-1"))), 1L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projections,
                points,
                items);

        VectorSearchVisualizationResult result = service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of("attachment"),
                10,
                null,
                null,
                null));

        assertThat(result.results()).singleElement()
                .extracting(VectorSearchVisualizationResult.ResultPoint::vectorItemId)
                .isEqualTo("chunk-1");
        ArgumentCaptor<VectorSearchRequest> requestCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStorePort).searchWithFilter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().metadataFilter().inCriteria())
                .containsEntry("objectType", List.of("attachment"));
        assertThat(requestCaptor.getValue().includeText()).isFalse();
        assertThat(requestCaptor.getValue().includeMetadata()).isTrue();
    }

    @Test
    void searchUsesRequestedEmbeddingProviderAndModel() {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(embeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(), 0L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                embeddingPort,
                vectorStorePort,
                projections,
                new FakePointRepository(List.of()),
                items);

        service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null,
                "kure",
                "nlpai-lab/KURE-v1"));

        ArgumentCaptor<EmbeddingRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embeddingPort).embed(requestCaptor.capture());
        assertThat(requestCaptor.getValue().provider()).isEqualTo("kure");
        assertThat(requestCaptor.getValue().model()).isEqualTo("nlpai-lab/KURE-v1");
    }

    @Test
    void searchRoutesRequestedEmbeddingProviderThroughRegistry() {
        EmbeddingPort defaultEmbeddingPort = mock(EmbeddingPort.class);
        EmbeddingPort kureEmbeddingPort = mock(EmbeddingPort.class);
        VectorStorePort vectorStorePort = mock(VectorStorePort.class);
        VectorProjectionRepository projections = mock(VectorProjectionRepository.class);
        ExistingVectorItemRepository items = mock(ExistingVectorItemRepository.class);
        AiProviderRegistry registry = new AiProviderRegistry(
                "gemini",
                "gemini",
                "gemini",
                Map.of("gemini", mock(ChatPort.class)),
                Map.of("gemini", defaultEmbeddingPort, "kure", kureEmbeddingPort));
        when(projections.findById("proj-1")).thenReturn(Optional.of(projection()));
        when(kureEmbeddingPort.embed(any())).thenReturn(new EmbeddingResponse(List.of(
                new EmbeddingVector("query", List.of(0.1, 0.2)))));
        when(vectorStorePort.searchWithFilter(any(VectorSearchRequest.class))).thenReturn(VectorSearchResults.of(List.of(), 0L));
        DefaultVectorSearchVisualizationService service = new DefaultVectorSearchVisualizationService(
                defaultEmbeddingPort,
                vectorStorePort,
                projections,
                new FakePointRepository(List.of()),
                items,
                registry);

        service.search(new VectorSearchVisualizationCommand(
                "proj-1",
                "java",
                List.of(),
                10,
                null,
                "kure",
                "nlpai-lab/KURE-v1"));

        verify(kureEmbeddingPort).embed(any(EmbeddingRequest.class));
        org.mockito.Mockito.verifyNoInteractions(defaultEmbeddingPort);
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
