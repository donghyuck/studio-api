package studio.one.platform.ai.web.dto;

import java.util.Map;

/**
 * DTO for vector search hits.
 */
public record VectorSearchResultDto(
        String documentId,
        String content,
        Map<String, Object> metadata,
        double score
) {
}
