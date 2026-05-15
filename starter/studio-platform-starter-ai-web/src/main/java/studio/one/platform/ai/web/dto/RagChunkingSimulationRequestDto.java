package studio.one.platform.ai.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RagChunkingSimulationRequestDto(
        @NotBlank String text,
        String objectType,
        String objectId,
        String attachmentId,
        String embeddingProvider,
        String embeddingModel,
        Boolean tokenizerAutoDetect,
        String chunkUnit,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunkSize) {
}
