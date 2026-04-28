package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Chat 요청에 RAG 검색을 결합하기 위한 DTO.
 */
public record ChatRagRequestDto(
        @NotNull @Valid ChatRequestDto chat,
        String ragQuery,
        @Min(value = 1, message = "ragTopK must be at least 1")
        @Max(value = 100, message = "ragTopK must be at most 100")
        Integer ragTopK,
        String objectType,
        String objectId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 100, message = "topK must be at most 100")
        Integer topK,
        @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore,
        Boolean debug
) {
    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            Boolean debug) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null, null, debug);
    }
}
