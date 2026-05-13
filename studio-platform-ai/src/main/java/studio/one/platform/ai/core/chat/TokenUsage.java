package studio.one.platform.ai.core.chat;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider-neutral token usage metadata.
 */
public final class TokenUsage {

    private final Integer inputTokens;
    private final Integer outputTokens;
    private final Integer totalTokens;

    public TokenUsage(
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens
    ) {
                inputTokens = nonNegative(inputTokens);
                outputTokens = nonNegative(outputTokens);
                totalTokens = nonNegative(totalTokens);
        
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public Integer totalTokens() {
        return totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TokenUsage)) {
            return false;
        }
        TokenUsage that = (TokenUsage) o;
        return java.util.Objects.equals(inputTokens, that.inputTokens)
                && java.util.Objects.equals(outputTokens, that.outputTokens)
                && java.util.Objects.equals(totalTokens, that.totalTokens);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(inputTokens, outputTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "TokenUsage[" +
                "inputTokens=" + inputTokens + ", " +
                "outputTokens=" + outputTokens + ", " +
                "totalTokens=" + totalTokens +
                "]";
    }

    public static final String KEY_INPUT_TOKENS = "inputTokens";
    public static final String KEY_OUTPUT_TOKENS = "outputTokens";
    public static final String KEY_TOTAL_TOKENS = "totalTokens";
    public static final String LEGACY_PROMPT_TOKENS = "promptTokens";
    public static final String LEGACY_COMPLETION_TOKENS = "completionTokens";
    public static final String LEGACY_TOTAL_TOKENS_SNAKE_CASE = "total_tokens";

    public static TokenUsage empty() {
        return new TokenUsage(null, null, null);
    }

    public static TokenUsage of(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        return new TokenUsage(inputTokens, outputTokens, totalTokens);
    }

    public static TokenUsage from(Object value) {
        if (value instanceof TokenUsage) {
            return (TokenUsage) value;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
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
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return Integer.parseInt((String) value);
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
