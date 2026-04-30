package studio.one.platform.ai.web.dto.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectionDetailResponse(
        String projectionId,
        String name,
        String algorithm,
        String status,
        List<String> targetTypes,
        Map<String, Object> filters,
        int itemCount,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {
}
