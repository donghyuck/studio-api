package studio.one.platform.ai.web.dto;

import java.time.Instant;

public record ConversationSummaryDto(
        String conversationId,
        String title,
        String summary,
        int messageCount,
        Instant lastUpdatedAt,
        String status) {
}
