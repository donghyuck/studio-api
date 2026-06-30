package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record RagRetrievalEvaluationQuestionSetRunRequestDto(
        @NotEmpty List<String> strategies,
        Integer topK,
        Double minScore,
        String objectType,
        String objectId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        ChatRagRetrievalOptionsDto retrievalOptions) {
}
