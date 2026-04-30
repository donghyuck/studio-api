package studio.one.platform.ai.core.vector.visualization;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class VectorVisualizationMetadataSanitizer {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "objectType",
            "objectId",
            "documentId",
            "chunkId",
            "chunkIndex",
            "chunkOrder",
            "chunkType",
            "sourceName",
            "title",
            "filename",
            "fileName",
            "name",
            "headingPath",
            "sourceRef",
            "page",
            "slide",
            "sourceFormat",
            "embeddingProvider",
            "embeddingProfileId",
            "embeddingModel",
            "embeddingDimension",
            "createdAt",
            "indexedAt");

    private VectorVisualizationMetadataSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (ALLOWED_KEYS.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
