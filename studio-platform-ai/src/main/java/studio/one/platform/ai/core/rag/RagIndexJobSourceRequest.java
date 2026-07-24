package studio.one.platform.ai.core.rag;

import java.util.List;
import java.util.Map;

public record RagIndexJobSourceRequest(
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        String chunkSetId,
        boolean requirePreparedChunks,
        String embeddingDeploymentId) {

    public RagIndexJobSourceRequest(
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            String chunkSetId,
            boolean requirePreparedChunks) {
        this(metadata, keywords, useLlmKeywordExtraction, embeddingProfileId, embeddingProvider, embeddingModel,
                chunkSetId, requirePreparedChunks, null);
    }

    public RagIndexJobSourceRequest(
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel) {
        this(metadata, keywords, useLlmKeywordExtraction, embeddingProfileId, embeddingProvider, embeddingModel,
                null, false);
    }

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
        chunkSetId = normalize(chunkSetId);
        embeddingDeploymentId = normalize(embeddingDeploymentId);
        if (embeddingDeploymentId != null
                && (embeddingProfileId != null || embeddingProvider != null || embeddingModel != null)) {
            throw new IllegalArgumentException(
                    "embeddingDeploymentId must not be supplied with legacy embedding selection fields");
        }
        if (requirePreparedChunks && chunkSetId == null) {
            throw new IllegalArgumentException("chunkSetId is required when prepared chunks are required");
        }
    }

    public static RagIndexJobSourceRequest empty() {
        return new RagIndexJobSourceRequest(Map.of(), List.of(), false);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
