package studio.one.platform.ai.web.dto;

import java.time.Instant;

public record RagRetrievalEvaluationJobDto(
        String jobId,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        int totalQuestions,
        int completedQuestions,
        int totalStrategies,
        int completedStrategies,
        String currentStrategy,
        String currentQuestion,
        String runId,
        String errorMessage) {
}
