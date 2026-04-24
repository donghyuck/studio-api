package studio.one.platform.ai.core.chat;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider-neutral token usage metadata.
 */
public record TokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {

    public static final String KEY_INPUT_TOKENS = "inputTokens";
    public static final String KEY_OUTPUT_TOKENS = "outputTokens";
    public static final String KEY_TOTAL_TOKENS = "totalTokens";
    public static final String LEGACY_PROMPT_TOKENS = "promptTokens";
    public static final String LEGACY_COMPLETION_TOKENS = "completionTokens";
    public static final String LEGACY_TOTAL_TOKENS_SNAKE_CASE = "total_tokens";

    public TokenUsage {
        inputTokens = nonNegative(inputTokens);
        outputTokens = nonNegative(outputTokens);
        totalTokens = nonNegative(totalTokens);
    }

    public static TokenUsage empty() {
        return new TokenUsage(null, null, null);
    }

    public static TokenUsage of(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        return new TokenUsage(inputTokens, outputTokens, totalTokens);
    }

    public static TokenUsage from(Object value) {
        if (value instanceof TokenUsage tokenUsage) {
            return tokenUsage;
        }
        if (value instanceof Map<?, ?> map) {
            return new TokenUsage(
                    integerValue(map.get(KEY_INPUT_TOKENS), map.get(LEGACY_PROMPT_TOKENS)),
                    integerValue(map.get(KEY_OUTPUT_TOKENS), map.get(LEGACY_COMPLETION_TOKENS)),
                    integerValue(map.get(KEY_TOTAL_TOKENS), map.get(LEGACY_TOTAL_TOKENS_SNAKE_CASE)));
        }
        return empty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(KEY_INPUT_TOKENS, inputTokens);
        metadata.put(KEY_OUTPUT_TOKENS, outputTokens);
        metadata.put(KEY_TOTAL_TOKENS, totalTokens);
        return ChatMetadataMaps.compact(metadata);
    }

    private static Integer integerValue(Object primary, Object fallback) {
        Object value = primary == null ? fallback : primary;
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }
}
