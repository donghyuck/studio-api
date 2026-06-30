package studio.one.platform.ai.web.dto;

import java.time.Instant;

public record RagRetrievalPolicyUsageDto(
        String usageId,
        String objectType,
        String objectId,
        String retrievalStrategy,
        String questionSetId,
        String evaluationRunId,
        Integer topK,
        Double minScore,
        int resultCount,
        boolean skippedChat,
        long elapsedMs,
        Instant createdAt
) {
}
