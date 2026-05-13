package studio.one.platform.ai.core.chat;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-neutral conversation aggregate metadata.
 */
public final class ChatConversation {

    private final String conversationId;
    private final String ownerId;
    private final String title;
    private final String summary;
    private final ConversationStatus status;
    private final String parentConversationId;
    private final String forkedFromMessageId;
    private final int messageCount;
    private final Instant createdAt;
    private final Instant lastUpdatedAt;
    private final Map<String, Object> metadata;

    public ChatConversation(
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
            Map<String, Object> metadata
    ) {
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
        
        this.conversationId = conversationId;
        this.ownerId = ownerId;
        this.title = title;
        this.summary = summary;
        this.status = status;
        this.parentConversationId = parentConversationId;
        this.forkedFromMessageId = forkedFromMessageId;
        this.messageCount = messageCount;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
        this.metadata = metadata;
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

    public ConversationStatus status() {
        return status;
    }

    public String parentConversationId() {
        return parentConversationId;
    }

    public String forkedFromMessageId() {
        return forkedFromMessageId;
    }

    public int messageCount() {
        return messageCount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatConversation)) {
            return false;
        }
        ChatConversation that = (ChatConversation) o;
        return java.util.Objects.equals(conversationId, that.conversationId)
                && java.util.Objects.equals(ownerId, that.ownerId)
                && java.util.Objects.equals(title, that.title)
                && java.util.Objects.equals(summary, that.summary)
                && java.util.Objects.equals(status, that.status)
                && java.util.Objects.equals(parentConversationId, that.parentConversationId)
                && java.util.Objects.equals(forkedFromMessageId, that.forkedFromMessageId)
                && messageCount == that.messageCount
                && java.util.Objects.equals(createdAt, that.createdAt)
                && java.util.Objects.equals(lastUpdatedAt, that.lastUpdatedAt)
                && java.util.Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(conversationId, ownerId, title, summary, status, parentConversationId, forkedFromMessageId, messageCount, createdAt, lastUpdatedAt, metadata);
    }

    @Override
    public String toString() {
        return "ChatConversation[" +
                "conversationId=" + conversationId + ", " +
                "ownerId=" + ownerId + ", " +
                "title=" + title + ", " +
                "summary=" + summary + ", " +
                "status=" + status + ", " +
                "parentConversationId=" + parentConversationId + ", " +
                "forkedFromMessageId=" + forkedFromMessageId + ", " +
                "messageCount=" + messageCount + ", " +
                "createdAt=" + createdAt + ", " +
                "lastUpdatedAt=" + lastUpdatedAt + ", " +
                "metadata=" + metadata +
                "]";
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
