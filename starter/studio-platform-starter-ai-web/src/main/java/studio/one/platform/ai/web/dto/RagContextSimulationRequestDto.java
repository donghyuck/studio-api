package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RagContextSimulationRequestDto(
        @NotBlank String query,
        String objectType,
        String objectId,
        Integer topK,
        Integer contextBudgetTokens,
        Boolean includeNeighborChunks,
        Boolean includeParentChunk,
        String embeddingProvider,
        String embeddingModel,
        Double minScore) {
}
