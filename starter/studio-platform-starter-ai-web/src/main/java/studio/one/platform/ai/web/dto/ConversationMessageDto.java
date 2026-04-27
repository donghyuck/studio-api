package studio.one.platform.ai.web.dto;

import java.time.Instant;
import java.util.Map;

public record ConversationMessageDto(
        String messageId,
        String role,
        String content,
        Instant createdAt,
        Map<String, Object> metadata) {
}
