package studio.one.platform.ai.web.dto;

import javax.validation.constraints.NotBlank;

/**
 * 요청된 질문을 검색에 적합한 형태로 리라이트하기 위한 DTO.
 */
public record QueryRewriteRequestDto(
        @NotBlank(message = "query must not be blank")
        String query
) {
}
