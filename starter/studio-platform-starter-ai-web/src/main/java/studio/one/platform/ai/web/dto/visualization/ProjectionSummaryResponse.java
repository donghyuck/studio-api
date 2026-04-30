package studio.one.platform.ai.web.dto.visualization;

import java.time.Instant;
import java.util.List;

public record ProjectionSummaryResponse(
        String projectionId,
        String name,
        String algorithm,
        String status,
        List<String> targetTypes,
        int itemCount,
        Instant createdAt,
        Instant completedAt) {
}
