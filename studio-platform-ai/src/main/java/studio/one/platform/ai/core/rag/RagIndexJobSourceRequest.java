package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

public record RagIndexJobSourceRequest(
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction) {

    public RagIndexJobSourceRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public static RagIndexJobSourceRequest empty() {
        return new RagIndexJobSourceRequest(Map.of(), List.of(), false);
    }
}
