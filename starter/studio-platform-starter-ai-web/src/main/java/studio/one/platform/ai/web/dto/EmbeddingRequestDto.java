package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

import studio.one.platform.ai.core.embedding.EmbeddingInputType;

/**
 * DTO describing an embedding request.
 */
public record EmbeddingRequestDto(
        @NotEmpty(message = "At least one text is required for embedding")
        List<String> texts,
        String provider,
        String model,
        EmbeddingInputType inputType,
        Map<String, Object> metadata
) {
    public EmbeddingRequestDto(List<String> texts) {
        this(texts, null, null, null, null);
    }
}
