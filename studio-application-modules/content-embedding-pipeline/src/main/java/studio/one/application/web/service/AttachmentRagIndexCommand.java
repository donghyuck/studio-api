package studio.one.application.web.service;

import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.rag.RagChunkingOptions;

public record AttachmentRagIndexCommand(
        String documentId,
        String objectType,
        String objectId,
        Map<String, Object> metadata,
        List<String> keywords,
        boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        RagChunkingOptions chunkingOptions) {

    public AttachmentRagIndexCommand(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction,
                null, null, null, RagChunkingOptions.empty());
    }

    public AttachmentRagIndexCommand(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction,
                embeddingProfileId, embeddingProvider, embeddingModel, RagChunkingOptions.empty());
    }

    public AttachmentRagIndexCommand {
        documentId = normalize(documentId);
        objectType = normalize(objectType);
        objectId = normalize(objectId);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        embeddingProfileId = normalize(embeddingProfileId);
        embeddingProvider = normalize(embeddingProvider);
        embeddingModel = normalize(embeddingModel);
        chunkingOptions = chunkingOptions == null ? RagChunkingOptions.empty() : chunkingOptions;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
