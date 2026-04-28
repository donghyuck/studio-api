package studio.one.application.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record SearchRequest(
        @NotBlank String query,
        @Min(1) @Max(100) Integer topK,
        String objectType,
        String objectId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        @DecimalMin(value = "0.0", message = "minScore must be at least 0.0")
        @DecimalMax(value = "1.0", message = "minScore must be at most 1.0")
        Double minScore
) {
    public SearchRequest(String query, int topK) {
        this(query, topK, null, null, null, null, null, null);
    }

    public SearchRequest(String query, int topK, String objectType, String objectId) {
        this(query, topK, objectType, objectId, null, null, null, null);
    }
}
