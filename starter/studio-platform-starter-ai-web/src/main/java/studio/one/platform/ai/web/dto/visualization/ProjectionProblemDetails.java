package studio.one.platform.ai.web.dto.visualization;

import java.time.OffsetDateTime;

public record ProjectionProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        Long totalCount,
        Integer maxAllowed,
        Integer sampleSize,
        OffsetDateTime timestamp) {
}
