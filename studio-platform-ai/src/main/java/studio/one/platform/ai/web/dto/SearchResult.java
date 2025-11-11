package studio.one.platform.ai.web.dto;

import java.util.Map;

public record SearchResult(String documentId,
                           String content,
                           Map<String, Object> metadata,
                           double score) {
}
