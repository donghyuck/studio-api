package studio.one.platform.ai.core.chat;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider-neutral chat streaming event.
 */
public final class ChatStreamEvent {

    private final ChatStreamEventType type;
    private final String delta;
    private final String model;
    private final ChatResponseMetadata metadata;
    private final String errorMessage;

    public ChatStreamEvent(
            ChatStreamEventType type,
            String delta,
            String model,
            ChatResponseMetadata metadata,
            String errorMessage
    ) {
                type = type == null ? ChatStreamEventType.DELTA : type;
                delta = delta == null ? "" : delta;
                model = model == null ? "" : model;
                metadata = metadata == null ? ChatResponseMetadata.empty() : metadata;
                errorMessage = errorMessage == null ? "" : errorMessage;
        
        this.type = type;
        this.delta = delta;
        this.model = model;
        this.metadata = metadata;
        this.errorMessage = errorMessage;
    }

    public ChatStreamEventType type() {
        return type;
    }

    public String delta() {
        return delta;
    }

    public String model() {
        return model;
    }

    public ChatResponseMetadata metadata() {
        return metadata;
    }

    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatStreamEvent)) {
            return false;
        }
        ChatStreamEvent that = (ChatStreamEvent) o;
        return java.util.Objects.equals(type, that.type)
                && java.util.Objects.equals(delta, that.delta)
                && java.util.Objects.equals(model, that.model)
                && java.util.Objects.equals(metadata, that.metadata)
                && java.util.Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, delta, model, metadata, errorMessage);
    }

    @Override
    public String toString() {
        return "ChatStreamEvent[" +
                "type=" + type + ", " +
                "delta=" + delta + ", " +
                "model=" + model + ", " +
                "metadata=" + metadata + ", " +
                "errorMessage=" + errorMessage +
                "]";
    }

    public static ChatStreamEvent delta(String delta, String model, ChatResponseMetadata metadata) {
        return new ChatStreamEvent(ChatStreamEventType.DELTA, delta, model, metadata, "");
    }

    public static ChatStreamEvent usage(ChatResponseMetadata metadata) {
        return new ChatStreamEvent(ChatStreamEventType.USAGE, "", "", metadata, "");
    }

    public static ChatStreamEvent complete(String model, ChatResponseMetadata metadata) {
        return new ChatStreamEvent(ChatStreamEventType.COMPLETE, "", model, metadata, "");
    }

    public static ChatStreamEvent error(String errorMessage, ChatResponseMetadata metadata) {
        return new ChatStreamEvent(ChatStreamEventType.ERROR, "", "", metadata, errorMessage);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type.value());
        event.put("delta", delta);
        event.put("model", model);
        event.put("metadata", metadata.toMap());
        event.put("errorMessage", errorMessage);
        return ChatMetadataMaps.compact(event);
    }
}
