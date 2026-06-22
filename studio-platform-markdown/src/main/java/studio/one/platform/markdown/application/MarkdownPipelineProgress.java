package studio.one.platform.markdown.application;

import studio.one.platform.markdown.domain.MarkdownPipelineExecution;

public record MarkdownPipelineProgress(
        MarkdownPipelineExecution pipeline,
        RagProgress rag) {

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
