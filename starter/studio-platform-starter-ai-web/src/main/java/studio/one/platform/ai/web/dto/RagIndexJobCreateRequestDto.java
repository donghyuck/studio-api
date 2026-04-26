package studio.one.platform.ai.web.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record RagIndexJobCreateRequestDto(
        @NotBlank String objectType,
        @NotBlank String objectId,
        String documentId,
        String sourceType,
        Boolean forceReindex,
        String text,
        Map<String, Object> metadata,
        List<String> keywords,
        Boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel) {
    public RagIndexJobCreateRequestDto(
            String objectType,
            String objectId,
            String documentId,
            String sourceType,
            Boolean forceReindex,
            String text,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(objectType, objectId, documentId, sourceType, forceReindex, text, metadata, keywords,
                useLlmKeywordExtraction, null, null, null);
    }
}
