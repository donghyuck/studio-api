package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConversationDetailDto(
        String conversationId,
        String title,
        String summary,
        String status,
        String parentConversationId,
        String forkedFromMessageId,
        int messageCount,
        Instant createdAt,
        Instant lastUpdatedAt,
        Map<String, Object> metadata,
        List<ConversationMessageDto> messages) {
}
