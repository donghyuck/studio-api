package studio.one.application.web.dto;

import java.util.Map;

public record SearchResult(
        String documentId,
        String content,
        Map<String, Object> metadata,
        double score
) {
}
