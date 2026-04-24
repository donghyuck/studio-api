package studio.one.platform.ai.core.chat;

import java.time.Instant;
import java.util.Map;

/**
 * Message persisted as part of a chat conversation.
 */
public record ChatConversationMessage(
        String messageId,
        String conversationId,
        ChatMessage message,
        String parentMessageId,
        boolean active,
        Instant createdAt,
        Map<String, Object> metadata) {

    public ChatConversationMessage {
        messageId = required(messageId, "messageId");
        conversationId = required(conversationId, "conversationId");
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        parentMessageId = normalize(parentMessageId);
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        metadata = ChatMetadataMaps.compact(metadata);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
