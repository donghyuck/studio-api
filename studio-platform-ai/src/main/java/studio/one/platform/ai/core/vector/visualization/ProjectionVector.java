package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

public record ProjectionVector(
        String vectorItemId,
        Long documentChunkId,
        String targetType,
        String sourceId,
        String label,
        Map<String, Object> metadata,
        double[] embedding,
        String embeddingModel,
        Integer embeddingDimension,
        Instant createdAt) {

    public ProjectionVector {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        embedding = embedding == null ? new double[0] : Arrays.copyOf(embedding, embedding.length);
    }
}
