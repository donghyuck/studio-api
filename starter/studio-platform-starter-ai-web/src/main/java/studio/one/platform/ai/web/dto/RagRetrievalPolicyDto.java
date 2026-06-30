package studio.one.platform.ai.web.dto;

import java.time.Instant;

public record RagRetrievalPolicyDto(
        String objectType,
        String objectId,
        String retrievalStrategy,
        ChatRagRetrievalOptionsDto retrievalOptions,
        String questionSetId,
        String evaluationRunId,
        Double score,
        Double hitRate,
        Double mrr,
        Double averageElapsedMs,
        Instant createdAt,
        Instant updatedAt
) {
}
