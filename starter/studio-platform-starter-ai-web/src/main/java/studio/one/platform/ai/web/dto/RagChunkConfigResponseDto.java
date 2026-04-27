package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagChunkConfigResponseDto(
        ChunkingConfigDto chunking,
        LegacyFallbackConfigDto legacyFallback,
        RagContextConfigDto ragContext,
        ChunkPreviewLimitsDto limits) {

    public record ChunkingConfigDto(
            boolean available,
            boolean enabled,
            String strategy,
            int maxSize,
            int overlap,
            List<String> availableStrategies,
            List<String> registeredChunkers,
            boolean chunkingOrchestratorAvailable) {
    }

    public record LegacyFallbackConfigDto(
            int chunkSize,
            int chunkOverlap,
            boolean textChunkerAvailable) {
    }

    public record RagContextConfigDto(
            int maxChunks,
            int maxChars,
            boolean includeScores,
            RagContextExpansionConfigDto expansion) {
    }

    public record RagContextExpansionConfigDto(
            boolean enabled,
            int candidateMultiplier,
            int maxCandidates,
            int previousWindow,
            int nextWindow,
            boolean includeParentContent) {
    }

    public record ChunkPreviewLimitsDto(
            boolean enabled,
            int maxInputChars,
            int maxPreviewChunks) {
    }
}
