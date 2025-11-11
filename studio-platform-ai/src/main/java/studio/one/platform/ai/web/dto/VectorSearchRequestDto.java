package studio.one.platform.ai.web.dto;

import javax.validation.constraints.Min;
import java.util.List;

/**
 * DTO for searching the vector store.
 */
public record VectorSearchRequestDto(
        String query,
        List<Double> embedding,
        @Min(value = 1, message = "topK must be at least 1") Integer topK
) {
    public VectorSearchRequestDto {
        if (topK == null) {
            topK = 5;
        }
    }
}
