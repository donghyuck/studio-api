package studio.one.application.web.dto;

import java.util.List;

public record EmbeddingVectorDto(
        String referenceId,
        List<Double> values
) {
}
