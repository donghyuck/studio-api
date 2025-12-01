package studio.one.platform.ai.web.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public record SearchRequest(
                @NotBlank String query,
                @Min(1) int topK) {
}
