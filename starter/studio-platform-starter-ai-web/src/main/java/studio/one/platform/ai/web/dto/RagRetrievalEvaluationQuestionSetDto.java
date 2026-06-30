package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.List;

public record RagRetrievalEvaluationQuestionSetDto(
        String questionSetId,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<RagRetrievalEvaluationRequestDto.Question> questions) {
}
