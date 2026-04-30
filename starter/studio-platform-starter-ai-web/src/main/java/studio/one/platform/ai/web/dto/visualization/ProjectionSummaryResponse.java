package studio.one.platform.ai.web.dto.visualization;

import java.time.Instant;

public record ProjectionSummaryResponse(
        String projectionId,
        String name,
        String algorithm,
        String status,
        int itemCount,
        Instant createdAt,
        Instant completedAt) {
}
