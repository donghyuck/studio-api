package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

public record RagIndexJobSourceRequest(
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel) {

    public RagIndexJobSourceRequest(
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction) {
        this(metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }

    public RagIndexJobSourceRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
    }

    public static RagIndexJobSourceRequest empty() {
        return new RagIndexJobSourceRequest(Map.of(), List.of(), false);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
