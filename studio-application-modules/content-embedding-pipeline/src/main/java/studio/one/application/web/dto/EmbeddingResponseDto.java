package studio.one.application.web.dto;

import java.util.List;

public record EmbeddingResponseDto(
        List<EmbeddingVectorDto> vectors
) {
}
