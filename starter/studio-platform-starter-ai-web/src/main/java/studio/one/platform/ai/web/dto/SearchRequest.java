package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
                @NotBlank String query,
                @Min(1) int topK,
                String objectType,
                String objectId) {

    public SearchRequest(String query, int topK) {
        this(query, topK, null, null);
    }
}
