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
        Boolean useLlmKeywordExtraction) {
}
