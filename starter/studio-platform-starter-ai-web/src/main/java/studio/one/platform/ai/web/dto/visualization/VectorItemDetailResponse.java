package studio.one.platform.ai.web.dto.visualization;

import java.time.Instant;
import java.util.Map;

public record VectorItemDetailResponse(
        String vectorItemId,
        String targetType,
        String sourceId,
        String label,
        String text,
        String embeddingModel,
        Integer dimension,
        Map<String, Object> metadata,
        Instant createdAt) {
}
