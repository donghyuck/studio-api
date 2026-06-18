package studio.one.platform.ai.web.dto.visualization;

import java.time.Instant;
import java.util.List;

public record ProjectionSummaryResponse(
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
        Instant completedAt) {
}
