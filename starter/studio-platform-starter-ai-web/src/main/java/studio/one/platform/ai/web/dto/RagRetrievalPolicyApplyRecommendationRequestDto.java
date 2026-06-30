package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RagRetrievalPolicyApplyRecommendationRequestDto(
        @NotBlank String questionSetId,
        @NotBlank String objectType,
        @NotBlank String objectId
) {
}
