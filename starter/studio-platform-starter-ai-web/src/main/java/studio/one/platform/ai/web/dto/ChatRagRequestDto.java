package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
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
        Boolean debug
) {
    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, null);
    }

    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId,
            Boolean debug) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null, null, null, debug);
    }
}
