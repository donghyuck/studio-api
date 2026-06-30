package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record RagRetrievalEvaluationQuestionSetRequestDto(
        @NotBlank String name,
        String description,
        @NotEmpty @Valid List<RagRetrievalEvaluationRequestDto.Question> questions) {
}
