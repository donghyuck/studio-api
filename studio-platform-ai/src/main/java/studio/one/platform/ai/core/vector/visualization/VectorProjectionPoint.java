package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.Map;

public record VectorProjectionPoint(
        String projectionId,
        String vectorItemId,
        Long documentChunkId,
        String targetType,
        String sourceId,
        String label,
        Map<String, Object> metadataPreview,
        double x,
        double y,
        String clusterId,
        Integer displayOrder,
        Instant createdAt) {

    public VectorProjectionPoint {
        metadataPreview = metadataPreview == null ? Map.of() : Map.copyOf(metadataPreview);
    }
}
