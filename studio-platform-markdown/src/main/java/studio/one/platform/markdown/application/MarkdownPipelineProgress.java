package studio.one.platform.markdown.application;

import java.util.Map;

import studio.one.platform.markdown.domain.MarkdownPipelineExecution;

public record MarkdownPipelineProgress(
        MarkdownPipelineExecution pipeline,
        ChunkingProgress chunking,
        RagProgress rag) {

    public MarkdownPipelineProgress(MarkdownPipelineExecution pipeline, RagProgress rag) {
        this(pipeline, null, rag);
    }

    public record ChunkingProgress(
            int chunkCount,
            int ideaBlockCount,
            int fallbackCount,
            String qualityStatus,
            Map<String, Integer> fallbackReasonCounts,
            int sourceBlockTargetCount,
            int sourceBlockCoveredCount,
            double sourceBlockCoverage,
            Double averageConfidence) {
    }

    public record RagProgress(
            String jobId,
            String status,
            String currentStep,
            int chunkCount,
            int embeddedCount,
            int indexedCount,
            int warningCount,
            String errorMessage) {
    }
}
