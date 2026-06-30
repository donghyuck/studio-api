package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

public record RagRetrievalEvaluationRequestDto(
        @NotEmpty List<String> strategies,
        @NotEmpty @Valid List<Question> questions,
        Integer topK,
        Double minScore,
        String objectType,
        String objectId,
        String questionSetId,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        ChatRagRetrievalOptionsDto retrievalOptions) {

    public record Question(
            @NotBlank String query,
            List<String> expectedChunkIds,
            List<String> expectedDocumentChunkIds,
            List<String> expectedContentContains) {
    }
}
