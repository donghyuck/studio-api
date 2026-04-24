package studio.one.platform.ai.core.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed view over provider-neutral chat response metadata.
 */
public record ChatResponseMetadata(
        TokenUsage tokenUsage,
        Long latencyMs,
        String provider,
        String resolvedModel,
        Boolean memoryUsed,
        String conversationId,
        Map<String, Object> attributes) {

    public static final String KEY_TOKEN_USAGE = "tokenUsage";
    public static final String KEY_LATENCY_MS = "latencyMs";
    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_RESOLVED_MODEL = "resolvedModel";
    public static final String KEY_MEMORY_USED = "memoryUsed";
    public static final String KEY_CONVERSATION_ID = "conversationId";

    public ChatResponseMetadata {
        tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        provider = normalize(provider);
        resolvedModel = normalize(resolvedModel);
        conversationId = normalize(conversationId);
        attributes = ChatMetadataMaps.compact(attributes);
    }

    public static ChatResponseMetadata empty() {
        return new ChatResponseMetadata(TokenUsage.empty(), null, "", "", null, "", Map.of());
    }

    public static ChatResponseMetadata from(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return empty();
        }
        return new ChatResponseMetadata(
                TokenUsage.from(metadata.get(KEY_TOKEN_USAGE)),
                longValue(metadata.get(KEY_LATENCY_MS)),
                stringValue(metadata.get(KEY_PROVIDER)),
                firstNonBlank(stringValue(metadata.get(KEY_RESOLVED_MODEL)), stringValue(metadata.get("modelName"))),
                booleanValue(metadata.get(KEY_MEMORY_USED)),
                stringValue(metadata.get(KEY_CONVERSATION_ID)),
                metadata);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new LinkedHashMap<>(attributes);
        if (!tokenUsage.toMap().isEmpty()) {
            metadata.putIfAbsent(KEY_TOKEN_USAGE, tokenUsage.toMap());
        }
        putIfAbsent(metadata, KEY_LATENCY_MS, latencyMs);
        putIfAbsent(metadata, KEY_PROVIDER, provider);
        putIfAbsent(metadata, KEY_RESOLVED_MODEL, resolvedModel);
        putIfAbsent(metadata, KEY_MEMORY_USED, memoryUsed);
        putIfAbsent(metadata, KEY_CONVERSATION_ID, conversationId);
        return ChatMetadataMaps.compact(metadata);
    }

    private static void putIfAbsent(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        metadata.putIfAbsent(key, value);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static Long longValue(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }
}
