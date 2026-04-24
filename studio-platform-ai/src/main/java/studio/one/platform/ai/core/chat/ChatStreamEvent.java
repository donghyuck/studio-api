package studio.one.platform.ai.core.chat;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider-neutral chat streaming event.
 */
public record ChatStreamEvent(
        ChatStreamEventType type,
        String delta,
        String model,
        ChatResponseMetadata metadata,
        String errorMessage) {

    public ChatStreamEvent {
        type = type == null ? ChatStreamEventType.DELTA : type;
        delta = delta == null ? "" : delta;
        model = model == null ? "" : model;
        metadata = metadata == null ? ChatResponseMetadata.empty() : metadata;
        errorMessage = errorMessage == null ? "" : errorMessage;
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
