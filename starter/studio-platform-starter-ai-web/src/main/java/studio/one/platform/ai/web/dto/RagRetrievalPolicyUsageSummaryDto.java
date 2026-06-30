package studio.one.platform.ai.web.dto;

public record RagRetrievalPolicyUsageSummaryDto(
        String objectType,
        String objectId,
        int usageCount,
        double averageResultCount,
        double averageElapsedMs,
        int skippedChatCount,
        String latestStrategy
) {
}
