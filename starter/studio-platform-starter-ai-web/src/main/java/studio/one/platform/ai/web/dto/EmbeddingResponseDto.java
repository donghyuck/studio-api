package studio.one.platform.ai.web.dto;

import java.util.List;

/**
 * DTO describing the response from an embedding request.
 */
public record EmbeddingResponseDto(
        List<EmbeddingVectorDto> vectors
) {
}
