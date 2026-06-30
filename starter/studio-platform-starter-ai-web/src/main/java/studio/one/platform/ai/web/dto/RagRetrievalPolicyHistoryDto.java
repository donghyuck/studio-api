package studio.one.platform.ai.web.dto;

import java.time.Instant;

public record RagRetrievalPolicyHistoryDto(
        String historyId,
        String objectType,
        String objectId,
        String retrievalStrategy,
        String reason,
        String questionSetId,
        String evaluationRunId,
        Double score,
        Double hitRate,
        Double mrr,
        Double averageElapsedMs,
        Instant createdAt
) {
}
