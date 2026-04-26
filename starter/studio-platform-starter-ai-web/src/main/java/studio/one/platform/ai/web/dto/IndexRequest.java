package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;

public record IndexRequest(
        @NotBlank String documentId,
        @NotBlank String text,
        Map<String, Object> metadata,
        List<String> keywords,
        Boolean useLlmKeywordExtraction,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel
) {
    public IndexRequest(
            String documentId,
            String text,
            Map<String, Object> metadata,
            List<String> keywords,
            Boolean useLlmKeywordExtraction) {
        this(documentId, text, metadata, keywords, useLlmKeywordExtraction, null, null, null);
    }
}
