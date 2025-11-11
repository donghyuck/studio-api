package studio.one.platform.ai.web.dto;

import java.util.List;

/**
 * DTO for a single embedding vector.
 */
public record EmbeddingVectorDto(
        String referenceId,
        List<Double> values
) {
}
