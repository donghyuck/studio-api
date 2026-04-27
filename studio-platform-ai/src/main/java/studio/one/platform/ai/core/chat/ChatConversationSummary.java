package studio.one.platform.ai.core.chat;

import java.time.Instant;

/**
 * Lightweight conversation list item.
 */
public record ChatConversationSummary(
        String conversationId,
        String ownerId,
        String title,
        String summary,
        int messageCount,
        Instant lastUpdatedAt,
        ConversationStatus status) {

    public ChatConversationSummary {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        conversationId = conversationId.trim();
        ownerId = ownerId == null ? "" : ownerId.trim();
        title = title == null ? "" : title.trim();
        summary = summary == null ? "" : summary.trim();
        messageCount = Math.max(0, messageCount);
        lastUpdatedAt = lastUpdatedAt == null ? Instant.EPOCH : lastUpdatedAt;
        status = status == null ? ConversationStatus.ACTIVE : status;
    }

    public static ChatConversationSummary from(ChatConversation conversation) {
        return new ChatConversationSummary(
                conversation.conversationId(),
                conversation.ownerId(),
                conversation.title(),
                conversation.summary(),
                conversation.messageCount(),
                conversation.lastUpdatedAt(),
                conversation.status());
    }
}
