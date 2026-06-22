package studio.one.platform.markdown.application;

import java.util.List;

public record MarkdownPipelineEstimate(
        String documentId,
        String revisionId,
        String sourceFileName,
        String sourceFormat,
        long sourceSizeBytes,
        int pageCount,
        int markdownLength,
        int estimatedChunkCount,
        int estimatedEmbeddingRequests,
        int embeddingBatchSize,
        String riskLevel,
        RecommendedChunking recommended,
        List<Warning> warnings) {

    public record RecommendedChunking(
            String chunkingStrategy,
            Integer chunkMaxSize,
            Integer chunkOverlap,
            String chunkUnit,
            int estimatedChunkCount,
            int estimatedEmbeddingRequests) {
    }

    public record Warning(String code, String message) {
    }
}
