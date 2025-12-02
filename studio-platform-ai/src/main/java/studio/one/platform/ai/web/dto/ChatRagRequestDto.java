package studio.one.platform.ai.web.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Chat 요청에 RAG 검색을 결합하기 위한 DTO.
 */
public record ChatRagRequestDto(
        @NotNull @Valid ChatRequestDto chat,
        String ragQuery,
        Integer ragTopK,
        String objectType,
        String objectId
) {
}
