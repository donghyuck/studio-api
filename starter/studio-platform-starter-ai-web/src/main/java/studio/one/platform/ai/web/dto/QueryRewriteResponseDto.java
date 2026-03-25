package studio.one.platform.ai.web.dto;

import java.util.List;

/**
 * 리라이트/확장된 쿼리 응답 DTO.
 */
public record QueryRewriteResponseDto(
        String originalQuery,
        String expandedQuery,
        List<String> keywords,
        String prompt,
        String rawResponse
) {
}
