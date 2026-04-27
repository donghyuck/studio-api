package studio.one.application.web.controller;

import java.util.List;
import java.util.Map;

/**
 * RAG 색인 시 외부에서 objectType/objectId/documentId 등을 전달받기 위한 DTO.
 */
public record AttachmentRagIndexRequestDto(
        String documentId,
        String objectType,
        String objectId,
        Map<String, Object> metadata,
        List<String> keywords,
        Boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        String chunkingStrategy,
        Integer chunkMaxSize,
        Integer chunkOverlap,
        String chunkUnit,
        Boolean debug
) {
    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction,
                null, null, null, null, null, null, null, null);
    }

    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction,
            Boolean debug) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction,
                null, null, null, null, null, null, null, debug);
    }

    public AttachmentRagIndexRequestDto(
            String documentId,
            String objectType,
            String objectId,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction,
            String embeddingProfileId,
            String embeddingProvider,
            String embeddingModel,
            Boolean debug) {
        this(documentId, objectType, objectId, metadata, keywords, useLlmKeywordExtraction,
                embeddingProfileId, embeddingProvider, embeddingModel, null, null, null, null, debug);
    }
}
