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
        String mode,
        long totalCount,
        int projectedCount,
        boolean sampled,
        Integer sampleSize,
        String samplingStrategy,
        int maxAllowed,
        int itemCount,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {
}
