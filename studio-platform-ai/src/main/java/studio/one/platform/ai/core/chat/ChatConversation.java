package studio.one.platform.ai.core.chat;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral conversation aggregate metadata.
 */
public record ChatConversation(
        String conversationId,
        String ownerId,
        String title,
        String summary,
        ConversationStatus status,
        String parentConversationId,
        String forkedFromMessageId,
        int messageCount,
        Instant createdAt,
        Instant lastUpdatedAt,
        Map<String, Object> metadata) {

    public ChatConversation {
        conversationId = required(conversationId, "conversationId");
        ownerId = normalize(ownerId);
        title = normalize(title);
        summary = normalize(summary);
        status = status == null ? ConversationStatus.ACTIVE : status;
        parentConversationId = normalize(parentConversationId);
        forkedFromMessageId = normalize(forkedFromMessageId);
        messageCount = Math.max(0, messageCount);
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        lastUpdatedAt = lastUpdatedAt == null ? createdAt : lastUpdatedAt;
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
