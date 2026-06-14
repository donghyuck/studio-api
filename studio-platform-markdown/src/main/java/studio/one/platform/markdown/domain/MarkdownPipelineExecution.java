package studio.one.platform.markdown.domain;

import java.time.Instant;

public record MarkdownPipelineExecution(
        String revisionId,
        MarkdownPipelineExecutionStatus status,
        MarkdownPipelineStage currentStage,
        MarkdownPipelineStage lastCompletedStage,
        int attemptCount,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
}
