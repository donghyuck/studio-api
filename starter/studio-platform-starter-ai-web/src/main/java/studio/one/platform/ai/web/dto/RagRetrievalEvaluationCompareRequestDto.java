package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RagRetrievalEvaluationCompareRequestDto(
        @NotBlank String beforeRunId,
        @NotBlank String afterRunId) {
}
