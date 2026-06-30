package studio.one.platform.ai.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record RagRetrievalEvaluationBenchmarkRequestDto(
        @NotEmpty List<@Valid ObjectScope> objects,
        @NotEmpty List<String> strategies,
        @Min(1) @Max(20) Integer repetitions,
        Integer topK,
        Double minScore,
        String embeddingProfileId,
        String embeddingProvider,
        String embeddingModel,
        ChatRagRetrievalOptionsDto retrievalOptions) {

    public record ObjectScope(
            @NotBlank String objectType,
            @NotBlank String objectId,
            String label) {
    }
}
