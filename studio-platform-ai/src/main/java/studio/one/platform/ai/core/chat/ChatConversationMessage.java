package studio.one.platform.ai.core.chat;

import java.time.Instant;
import java.util.Map;

/**
 * Message persisted as part of a chat conversation.
 */
public final class ChatConversationMessage {

    private final String messageId;
    private final String conversationId;
    private final ChatMessage message;
    private final String parentMessageId;
    private final boolean active;
    private final Instant createdAt;
    private final Map<String, Object> metadata;

    public ChatConversationMessage(
            String messageId,
            String conversationId,
            ChatMessage message,
            String parentMessageId,
            boolean active,
            Instant createdAt,
            Map<String, Object> metadata
    ) {
                messageId = required(messageId, "messageId");
                conversationId = required(conversationId, "conversationId");
                if (message == null) {
                    throw new IllegalArgumentException("message must not be null");
                }
                parentMessageId = normalize(parentMessageId);
                createdAt = createdAt == null ? Instant.EPOCH : createdAt;
                metadata = ChatMetadataMaps.compact(metadata);
        
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.message = message;
        this.parentMessageId = parentMessageId;
        this.active = active;
        this.createdAt = createdAt;
        this.metadata = metadata;
    }

    public String messageId() {
        return messageId;
    }

    public String conversationId() {
        return conversationId;
    }

    public ChatMessage message() {
        return message;
    }

    public String parentMessageId() {
        return parentMessageId;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatConversationMessage)) {
            return false;
        }
        ChatConversationMessage that = (ChatConversationMessage) o;
        return java.util.Objects.equals(messageId, that.messageId)
                && java.util.Objects.equals(conversationId, that.conversationId)
                && java.util.Objects.equals(message, that.message)
                && java.util.Objects.equals(parentMessageId, that.parentMessageId)
                && active == that.active
                && java.util.Objects.equals(createdAt, that.createdAt)
                && java.util.Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(messageId, conversationId, message, parentMessageId, active, createdAt, metadata);
    }

    @Override
    public String toString() {
        return "ChatConversationMessage[" +
                "messageId=" + messageId + ", " +
                "conversationId=" + conversationId + ", " +
                "message=" + message + ", " +
                "parentMessageId=" + parentMessageId + ", " +
                "active=" + active + ", " +
                "createdAt=" + createdAt + ", " +
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
