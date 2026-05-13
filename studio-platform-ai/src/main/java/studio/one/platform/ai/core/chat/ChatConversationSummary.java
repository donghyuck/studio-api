package studio.one.platform.ai.core.chat;

import java.time.Instant;

/**
 * Lightweight conversation list item.
 */
public final class ChatConversationSummary {

    private final String conversationId;
    private final String ownerId;
    private final String title;
    private final String summary;
    private final int messageCount;
    private final Instant lastUpdatedAt;
    private final ConversationStatus status;

    public ChatConversationSummary(
            String conversationId,
            String ownerId,
            String title,
            String summary,
            int messageCount,
            Instant lastUpdatedAt,
            ConversationStatus status
    ) {
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
        
        this.conversationId = conversationId;
        this.ownerId = ownerId;
        this.title = title;
        this.summary = summary;
        this.messageCount = messageCount;
        this.lastUpdatedAt = lastUpdatedAt;
        this.status = status;
    }

    public String conversationId() {
        return conversationId;
    }

    public String ownerId() {
        return ownerId;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public int messageCount() {
        return messageCount;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public ConversationStatus status() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatConversationSummary)) {
            return false;
        }
        ChatConversationSummary that = (ChatConversationSummary) o;
        return java.util.Objects.equals(conversationId, that.conversationId)
                && java.util.Objects.equals(ownerId, that.ownerId)
                && java.util.Objects.equals(title, that.title)
                && java.util.Objects.equals(summary, that.summary)
                && messageCount == that.messageCount
                && java.util.Objects.equals(lastUpdatedAt, that.lastUpdatedAt)
                && java.util.Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(conversationId, ownerId, title, summary, messageCount, lastUpdatedAt, status);
    }

    @Override
    public String toString() {
        return "ChatConversationSummary[" +
                "conversationId=" + conversationId + ", " +
                "ownerId=" + ownerId + ", " +
                "title=" + title + ", " +
                "summary=" + summary + ", " +
                "messageCount=" + messageCount + ", " +
                "lastUpdatedAt=" + lastUpdatedAt + ", " +
                "status=" + status +
                "]";
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
