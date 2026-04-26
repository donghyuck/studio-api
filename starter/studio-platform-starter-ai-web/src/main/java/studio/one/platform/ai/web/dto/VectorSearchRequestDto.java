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
        Double minScore,
        Boolean includeText,
        Boolean includeMetadata
) {
    public VectorSearchRequestDto(
            String query,
            List<Double> embedding,
            Integer topK,
            Boolean hybrid,
            String objectType,
            String objectId,
            Double minScore) {
        this(query, embedding, topK, hybrid, objectType, objectId, minScore, null, null);
    }

    public VectorSearchRequestDto {
        if (topK == null) {
            topK = 5;
        }
        if (hybrid == null) {
            hybrid = Boolean.FALSE;
        }
        if (includeText == null) {
            includeText = Boolean.TRUE;
        }
        if (includeMetadata == null) {
            includeMetadata = Boolean.TRUE;
        }
    }
}
