package studio.one.platform.ai.core.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed view over provider-neutral chat response metadata.
 */
public final class ChatResponseMetadata {

    private final TokenUsage tokenUsage;
    private final Long latencyMs;
    private final String provider;
    private final String resolvedModel;
    private final Boolean memoryUsed;
    private final String conversationId;
    private final Map<String, Object> attributes;

    public ChatResponseMetadata(
            TokenUsage tokenUsage,
            Long latencyMs,
            String provider,
            String resolvedModel,
            Boolean memoryUsed,
            String conversationId,
            Map<String, Object> attributes
    ) {
                tokenUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
                provider = normalize(provider);
                resolvedModel = normalize(resolvedModel);
                conversationId = normalize(conversationId);
                attributes = ChatMetadataMaps.compact(attributes);
        
        this.tokenUsage = tokenUsage;
        this.latencyMs = latencyMs;
        this.provider = provider;
        this.resolvedModel = resolvedModel;
        this.memoryUsed = memoryUsed;
        this.conversationId = conversationId;
        this.attributes = attributes;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public String provider() {
        return provider;
    }

    public String resolvedModel() {
        return resolvedModel;
    }

    public Boolean memoryUsed() {
        return memoryUsed;
    }

    public String conversationId() {
        return conversationId;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatResponseMetadata)) {
            return false;
        }
        ChatResponseMetadata that = (ChatResponseMetadata) o;
        return java.util.Objects.equals(tokenUsage, that.tokenUsage)
                && java.util.Objects.equals(latencyMs, that.latencyMs)
                && java.util.Objects.equals(provider, that.provider)
                && java.util.Objects.equals(resolvedModel, that.resolvedModel)
                && java.util.Objects.equals(memoryUsed, that.memoryUsed)
                && java.util.Objects.equals(conversationId, that.conversationId)
                && java.util.Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tokenUsage, latencyMs, provider, resolvedModel, memoryUsed, conversationId, attributes);
    }

    @Override
    public String toString() {
        return "ChatResponseMetadata[" +
                "tokenUsage=" + tokenUsage + ", " +
                "latencyMs=" + latencyMs + ", " +
                "provider=" + provider + ", " +
                "resolvedModel=" + resolvedModel + ", " +
                "memoryUsed=" + memoryUsed + ", " +
                "conversationId=" + conversationId + ", " +
                "attributes=" + attributes +
                "]";
    }

    public static final String KEY_TOKEN_USAGE = "tokenUsage";
    public static final String KEY_LATENCY_MS = "latencyMs";
    public static final String KEY_PROVIDER = "provider";
    public static final String KEY_RESOLVED_MODEL = "resolvedModel";
    public static final String KEY_MEMORY_USED = "memoryUsed";
    public static final String KEY_CONVERSATION_ID = "conversationId";

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
        if (value instanceof String && ((String) value).isBlank()) {
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
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
}
