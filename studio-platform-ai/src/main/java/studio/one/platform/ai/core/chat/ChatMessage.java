package studio.one.platform.ai.core.chat;

import java.util.Objects;

/**
 * A chat message consisting of a role and textual content.
 */
public final class ChatMessage {

    private final ChatMessageRole role;
    private final String content;

    public ChatMessage(ChatMessageRole role, String content) {
        this.role = Objects.requireNonNull(role, "role");
        this.content = normalizeContent(Objects.requireNonNull(content, "content"));
    }

    public ChatMessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatMessageRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatMessageRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatMessageRole.ASSISTANT, content);
    }

    private static String normalizeContent(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Chat message content cannot be blank");
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return role == that.role && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "role=" + role +
                ", content='" + content + '\'' +
                '}';
    }
}
