package studio.one.platform.ai.service.visualization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import studio.one.platform.ai.core.vector.visualization.ExistingVectorItemRepository;
import studio.one.platform.ai.core.vector.visualization.PcaVectorProjectionGenerator;
import studio.one.platform.ai.core.vector.visualization.ProjectionAlgorithm;
import studio.one.platform.ai.core.vector.visualization.ProjectionMode;
import studio.one.platform.ai.core.vector.visualization.ProjectionSamplingStrategy;
import studio.one.platform.ai.core.vector.visualization.ProjectionStatus;
import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionPointRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionRepository;
import studio.one.platform.ai.core.vector.visualization.VectorProjectionScope;

class DefaultVectorProjectionRecoveryTest {

    @Test
    void createUsesConfiguredDefaultSamplingStrategyWhenRequestOmitsStrategy() {
        VectorProjectionRepository projectionRepository = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository pointRepository = mock(VectorProjectionPointRepository.class);
        ExistingVectorItemRepository itemRepository = mock(ExistingVectorItemRepository.class);
        VectorProjectionJobService jobService = mock(VectorProjectionJobService.class);

        when(itemRepository.count(new VectorProjectionScope(List.of("document"), Map.of()))).thenReturn(2_000L);

        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projectionRepository,
                pointRepository,
                itemRepository,
                jobService,
                Runnable::run,
                1_000,
                500,
                ProjectionSamplingStrategy.HEAD,
                Duration.ofMinutes(3));

        service.create(new VectorProjectionCreateCommand(
                "Projection",
                ProjectionAlgorithm.PCA,
                List.of("document"),
                Map.of(),
                ProjectionMode.OVERVIEW,
                null,
                null,
                "tester"));

        ArgumentCaptor<VectorProjection> projectionCaptor = ArgumentCaptor.forClass(VectorProjection.class);
        verify(projectionRepository).save(projectionCaptor.capture());
        VectorProjection projection = projectionCaptor.getValue();

        org.assertj.core.api.Assertions.assertThat(projection.samplingStrategy()).isEqualTo(ProjectionSamplingStrategy.HEAD);
        org.assertj.core.api.Assertions.assertThat(projection.projectedCount()).isEqualTo(500);
    }

    @Test
    void listFailsStaleProcessingProjectionsBeforeReturningItems() {
        VectorProjectionRepository projectionRepository = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository pointRepository = mock(VectorProjectionPointRepository.class);
        ExistingVectorItemRepository itemRepository = mock(ExistingVectorItemRepository.class);
        VectorProjectionJobService jobService = mock(VectorProjectionJobService.class);

        DefaultVectorProjectionService service = new DefaultVectorProjectionService(
                projectionRepository,
                pointRepository,
                itemRepository,
                jobService,
                Runnable::run,
                1_000,
                1_000,
                Duration.ofMinutes(3));

        service.list(50, 0);

        verify(projectionRepository).markStaleProcessingFailed(
                any(Instant.class),
                eq("PROJECTION_JOB_STALE"),
                eq("Projection job exceeded processing timeout without writing points"),
                any(Instant.class));
        verify(projectionRepository).findAll(50, 0);
    }

    @Test
    void jobMarksOutOfMemoryAsFailedWithDedicatedErrorCode() {
        VectorProjectionRepository projectionRepository = mock(VectorProjectionRepository.class);
        VectorProjectionPointRepository pointRepository = mock(VectorProjectionPointRepository.class);
        ExistingVectorItemRepository itemRepository = mock(ExistingVectorItemRepository.class);
        VectorProjection projection = projection();

        when(projectionRepository.findById("projection-1")).thenReturn(Optional.of(projection));
        when(itemRepository.findProjectionVectors(
                new VectorProjectionScope(List.of("document"), Map.of()),
                ProjectionSamplingStrategy.STRATIFIED,
                100))
                .thenThrow(new OutOfMemoryError("heap exhausted"));

        DefaultVectorProjectionJobService jobService = new DefaultVectorProjectionJobService(
                projectionRepository,
                pointRepository,
                itemRepository,
                List.of(new PcaVectorProjectionGenerator()));

        jobService.run("projection-1");

        verify(projectionRepository).updateStatus("projection-1", ProjectionStatus.PROCESSING, null, null);
        verify(projectionRepository).updateStatus(
                eq("projection-1"),
                eq(ProjectionStatus.FAILED),
                eq("PROJECTION_JOB_OUT_OF_MEMORY"),
                eq("heap exhausted"),
                any(Instant.class));
    }

    private VectorProjection projection() {
        return new VectorProjection(
                "projection-1",
                "Projection",
                ProjectionAlgorithm.PCA,
                ProjectionStatus.PROCESSING,
                List.of("document"),
                Map.of(),
                ProjectionMode.OVERVIEW,
                100,
                100,
                false,
                null,
                ProjectionSamplingStrategy.STRATIFIED,
                1_000,
                0,
                null,
                null,
                "tester",
                Instant.now(),
                null);
    }
}
