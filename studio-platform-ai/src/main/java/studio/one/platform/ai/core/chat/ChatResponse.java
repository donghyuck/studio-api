package studio.one.platform.ai.core.chat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the response from a chat-capable AI provider.
 */
public final class ChatResponse {

    private final List<ChatMessage> messages;
    private final String model;
    private final Map<String, Object> metadata;

    public ChatResponse(List<ChatMessage> messages, String model, Map<String, Object> metadata) {
        Objects.requireNonNull(messages, "messages");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("At least one chat message is required in the response");
        }
        this.messages = List.copyOf(messages);
        this.model = model;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public List<ChatMessage> messages() {
        return Collections.unmodifiableList(messages);
    }

    public String model() {
        return model;
    }

    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "messages=" + messages +
                ", model='" + model + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
