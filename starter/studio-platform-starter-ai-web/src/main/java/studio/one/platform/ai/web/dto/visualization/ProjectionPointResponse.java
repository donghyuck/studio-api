package studio.one.platform.ai.web.dto.visualization;

import java.util.Map;

public record ProjectionPointResponse(
        String vectorItemId,
        String targetType,
        String sourceId,
        String label,
        double x,
        double y,
        String clusterId,
        Map<String, Object> metadata) {
}
