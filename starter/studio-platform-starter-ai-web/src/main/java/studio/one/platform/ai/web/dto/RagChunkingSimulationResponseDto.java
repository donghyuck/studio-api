package studio.one.platform.ai.web.dto;

import java.util.List;

public record RagChunkingSimulationResponseDto(
        TokenizerStatusDto tokenizer,
        List<ChunkDto> chunks,
        TokenDistributionDto tokenDistribution,
        int totalChunks,
        int totalChars,
        int totalTokens,
        List<String> warnings) {

    public record TokenizerStatusDto(
            String embeddingProvider,
            String embeddingModel,
            String tokenizerProvider,
            String tokenizerEncoding,
            String selectionSource,
            String confidence,
            String chunkUnit,
            int chunkSize,
            int chunkOverlap,
            boolean fallbackUsed,
            List<String> warnings) {
    }

    public record ChunkDto(
            String chunkId,
            String content,
            int tokenCount,
            int textLength,
            String tokenizerProvider,
            String tokenizerEncoding,
            String embeddingModel,
            String chunkType,
            Integer page,
            String section,
            List<String> warnings) {
    }

    public record TokenDistributionDto(
            int min,
            int max,
            double average) {
    }
}
