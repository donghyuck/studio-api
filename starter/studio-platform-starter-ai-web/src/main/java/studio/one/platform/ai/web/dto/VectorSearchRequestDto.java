package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * DTO for searching the vector store.
 */
public record VectorSearchRequestDto( 
        String query,
        List<Double> embedding,
        @Min(value = 1, message = "topK must be at least 1") Integer topK,
        Boolean hybrid,
        String objectType,
        String objectId,
        @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore
) {
    public VectorSearchRequestDto {
        if (topK == null) {
            topK = 5;
        }
        if (hybrid == null) {
            hybrid = Boolean.FALSE;
        }
    }
}
