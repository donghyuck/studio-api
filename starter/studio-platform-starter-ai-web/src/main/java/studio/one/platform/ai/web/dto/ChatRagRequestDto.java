package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Chat 요청에 RAG 검색을 결합하기 위한 DTO.
 */
public record ChatRagRequestDto(
        @NotNull @Valid ChatRequestDto chat,
        String ragQuery,
        Integer ragTopK,
        String objectType,
        String objectId,
        Boolean debug
) {
    public ChatRagRequestDto(
            ChatRequestDto chat,
            String ragQuery,
            Integer ragTopK,
            String objectType,
            String objectId) {
        this(chat, ragQuery, ragTopK, objectType, objectId, null);
    }
}
