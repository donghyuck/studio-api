package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VectorItem(
        String vectorItemId,
        String targetType,
        String sourceId,
        String label,
        String contentText,
        List<Double> embedding,
        String embeddingModel,
        Integer embeddingDimension,
        Map<String, Object> metadata,
        Instant createdAt) {

    public VectorItem {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
    }
}
