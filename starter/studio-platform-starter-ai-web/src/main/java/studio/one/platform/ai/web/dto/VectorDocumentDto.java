package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * DTO describing a document to persist in the vector store.
 */
public record VectorDocumentDto(
        @NotBlank String id,
        @NotBlank String content,
        Map<String, Object> metadata,
        @NotEmpty(message = "Embedding cannot be empty")
        List<Double> embedding
) {
}
