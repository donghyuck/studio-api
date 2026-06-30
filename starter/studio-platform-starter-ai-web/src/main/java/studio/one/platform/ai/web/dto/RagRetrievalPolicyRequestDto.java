package studio.one.platform.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record RagRetrievalPolicyRequestDto(
        @NotBlank String objectType,
        @NotBlank String objectId,
        @NotBlank String retrievalStrategy,
        @Valid ChatRagRetrievalOptionsDto retrievalOptions,
        String questionSetId,
        String evaluationRunId,
        Double score,
        Double hitRate,
        Double mrr,
        Double averageElapsedMs
) {
}
