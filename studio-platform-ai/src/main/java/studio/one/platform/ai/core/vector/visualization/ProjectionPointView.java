package studio.one.platform.ai.core.vector.visualization;

import java.util.Map;

public record ProjectionPointView(
        String vectorItemId,
        String targetType,
        String sourceId,
        String label,
        double x,
        double y,
        String clusterId,
        Map<String, Object> metadata) {

    public ProjectionPointView {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
