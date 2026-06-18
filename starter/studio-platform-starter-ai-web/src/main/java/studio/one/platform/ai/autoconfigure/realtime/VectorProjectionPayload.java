package studio.one.platform.ai.autoconfigure.realtime;

import java.time.Instant;
import java.util.List;

import studio.one.platform.ai.core.vector.visualization.VectorProjection;
import studio.one.platform.realtime.stomp.domain.model.RealtimePayload;

public record VectorProjectionPayload(
        String projectionId,
        String name,
        String algorithm,
        String status,
        List<String> targetTypes,
        String mode,
        long totalCount,
        int projectedCount,
        boolean sampled,
        Integer sampleSize,
        String samplingStrategy,
        int maxAllowed,
        int itemCount,
        Instant createdAt,
        Instant completedAt) implements RealtimePayload {

    static VectorProjectionPayload from(VectorProjection projection) {
        return new VectorProjectionPayload(
                projection.projectionId(),
                projection.name(),
                projection.algorithm().name(),
                projection.status().name(),
                projection.targetTypes(),
                projection.mode().name(),
                projection.totalCount(),
                projection.projectedCount(),
                projection.sampled(),
                projection.sampleSize(),
                projection.samplingStrategy().name(),
                projection.maxAllowed(),
                projection.itemCount(),
                projection.createdAt(),
                projection.completedAt());
    }
}
