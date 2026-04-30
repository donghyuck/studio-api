package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointPage;
import studio.one.platform.ai.core.vector.visualization.ProjectionPointView;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorItem;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.service.visualization.VectorProjectionCreateCommand;
import studio.one.platform.ai.service.visualization.VectorProjectionService;
import studio.one.platform.ai.service.visualization.VectorSearchVisualizationService;
import studio.one.platform.ai.web.dto.visualization.ProjectionCreateRequest;
import studio.one.platform.ai.web.dto.visualization.VectorSearchVisualizationRequest;

class VectorVisualizationMgmtControllerTest {

    @Test
    void createProjectionDefaultsToPcaAndReturnsRequestedStatus() {
        VectorProjectionService projectionService = mock(VectorProjectionService.class);
        VectorProjection projection = projection(ProjectionStatus.REQUESTED);
        when(projectionService.create(any())).thenReturn(projection);
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                projectionService,
                mock(VectorSearchVisualizationService.class));

        var response = controller.createProjection(new ProjectionCreateRequest(
                "NCS map",
                List.of("COURSE_CHUNK"),
                null,
                Map.of("useYn", "Y")));

        assertThat(response.getBody().getData().projectionId()).isEqualTo("proj-1");
        assertThat(response.getBody().getData().status()).isEqualTo("REQUESTED");
        verify(projectionService).create(any());
    }

    @Test
    void createProjectionAcceptsUmapAndTsneAlgorithms() {
        VectorProjectionService projectionService = mock(VectorProjectionService.class);
        when(projectionService.create(any())).thenReturn(projection(ProjectionStatus.REQUESTED));
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                projectionService,
                mock(VectorSearchVisualizationService.class));
        ArgumentCaptor<VectorProjectionCreateCommand> captor = ArgumentCaptor.forClass(VectorProjectionCreateCommand.class);

        controller.createProjection(new ProjectionCreateRequest("UMAP map", List.of(), "UMAP", Map.of()));
        controller.createProjection(new ProjectionCreateRequest("TSNE map", List.of(), "tsne", Map.of()));

        verify(projectionService, org.mockito.Mockito.times(2)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(VectorProjectionCreateCommand::algorithm)
                .containsExactly(ProjectionAlgorithm.UMAP, ProjectionAlgorithm.TSNE);
    }

    @Test
    void createProjectionRejectsUnsupportedAlgorithm() {
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                mock(VectorProjectionService.class),
                mock(VectorSearchVisualizationService.class));

        assertThatThrownBy(() -> controller.createProjection(new ProjectionCreateRequest(
                "bad map",
                List.of(),
                "MDS",
                Map.of())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("UNSUPPORTED_PROJECTION_ALGORITHM");
    }

    @Test
    void pointsReturnsClientOrientedShape() {
        VectorProjectionService projectionService = mock(VectorProjectionService.class);
        when(projectionService.get("proj-1")).thenReturn(projection(ProjectionStatus.COMPLETED));
        when(projectionService.points("proj-1", "COURSE_CHUNK", null, "java", 2000, 0))
                .thenReturn(new ProjectionPointPage(1, List.of(new ProjectionPointView(
                        "chunk-1",
                        "COURSE_CHUNK",
                        "course-1",
                        "Java 기본 문법",
                        0.1,
                        0.2,
                        null,
                        Map.of("chunkIndex", 3)))));
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                projectionService,
                mock(VectorSearchVisualizationService.class));

        var response = controller.points("proj-1", "COURSE_CHUNK", null, "java", 2000, 0);

        assertThat(response.getBody().getData().algorithm()).isEqualTo("PCA");
        assertThat(response.getBody().getData().items()).singleElement()
                .satisfies(point -> {
                    assertThat(point.vectorItemId()).isEqualTo("chunk-1");
                    assertThat(point.label()).isEqualTo("Java 기본 문법");
                    assertThat(point.metadata()).containsEntry("chunkIndex", 3);
                });
    }

    @Test
    void listProjectionIncludesActualTargetTypes() {
        VectorProjectionService projectionService = mock(VectorProjectionService.class);
        when(projectionService.list(50, 0)).thenReturn(List.of(projection(ProjectionStatus.COMPLETED)));
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                projectionService,
                mock(VectorSearchVisualizationService.class));

        var response = controller.listProjections(50, 0);

        assertThat(response.getBody().getData().items()).singleElement()
                .satisfies(item -> assertThat(item.targetTypes()).containsExactly("COURSE_CHUNK"));
    }

    @Test
    void itemDetailDoesNotReturnEmbeddingMetadata() {
        VectorProjectionService projectionService = mock(VectorProjectionService.class);
        when(projectionService.item("chunk-1")).thenReturn(new VectorItem(
                "chunk-1",
                "COURSE_CHUNK",
                "course-1",
                "label",
                "text",
                List.of(0.1, 0.2),
                "model",
                2,
                Map.of("embedding", List.of(0.1, 0.2), "chunkIndex", 1),
                Instant.now()));
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(projectionService, null);

        var response = controller.item("chunk-1");

        assertThat(response.getBody().getData().dimension()).isEqualTo(2);
        assertThat(response.getBody().getData().metadata()).doesNotContainKey("embedding");
    }

    @Test
    void searchVisualizationRequiresConfiguredSearchService() {
        VectorVisualizationMgmtController controller = new VectorVisualizationMgmtController(
                mock(VectorProjectionService.class),
                null);

        assertThatThrownBy(() -> controller.searchVisualization(new VectorSearchVisualizationRequest(
                "proj-1",
                "java",
                List.of(),
                10,
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .satisfies(status -> assertThat(status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private VectorProjection projection(ProjectionStatus status) {
        return new VectorProjection(
                "proj-1",
                "map",
                ProjectionAlgorithm.PCA,
                status,
                List.of("COURSE_CHUNK"),
                Map.of(),
                1,
                null,
                null,
                Instant.parse("2026-04-30T00:00:00Z"),
                null);
    }
}
