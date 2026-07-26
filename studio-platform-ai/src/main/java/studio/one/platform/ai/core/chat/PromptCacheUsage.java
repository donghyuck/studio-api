package studio.one.platform.ai.core.chat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider-neutral prompt cache token usage.
 *
 * <p>The three token buckets are mutually exclusive only when {@link #completeness()}
 * is {@link Completeness#COMPLETE}. Partial provider reports remain observable but
 * must not be used for cache-aware cost calculation.</p>
 */
public record PromptCacheUsage(
        Integer uncachedInputTokens,
        Integer cacheReadInputTokens,
        Integer cacheWriteInputTokens,
        Completeness completeness) {

    public static final String KEY_UNCACHED_INPUT_TOKENS = "uncachedInputTokens";
    public static final String KEY_CACHE_READ_INPUT_TOKENS = "cacheReadInputTokens";
    public static final String KEY_CACHE_WRITE_INPUT_TOKENS = "cacheWriteInputTokens";
    public static final String KEY_COMPLETENESS = "completeness";

    public PromptCacheUsage {
        uncachedInputTokens = nonNegative(uncachedInputTokens);
        cacheReadInputTokens = nonNegative(cacheReadInputTokens);
        cacheWriteInputTokens = nonNegative(cacheWriteInputTokens);
        completeness = completeness == null ? Completeness.PARTIAL : completeness;
    }

    public static PromptCacheUsage complete(
            Integer inputTokens,
            Integer cacheReadInputTokens,
            Integer cacheWriteInputTokens) {
        Integer input = nonNegative(inputTokens);
        Integer read = nonNegative(cacheReadInputTokens);
        Integer write = nonNegative(cacheWriteInputTokens);
        if (input == null || read == null || write == null || (long) read + write > input) {
            return new PromptCacheUsage(null, read, write, Completeness.PARTIAL);
        }
        return new PromptCacheUsage(input - read - write, read, write, Completeness.COMPLETE);
    }

    public static PromptCacheUsage partial(Integer cacheReadInputTokens, Integer cacheWriteInputTokens) {
        return new PromptCacheUsage(null, cacheReadInputTokens, cacheWriteInputTokens, Completeness.PARTIAL);
    }

    public static PromptCacheUsage from(Object value) {
        if (value instanceof PromptCacheUsage usage) {
            return usage;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        PromptCacheUsage usage = new PromptCacheUsage(
                integerValue(map.get(KEY_UNCACHED_INPUT_TOKENS)),
                integerValue(map.get(KEY_CACHE_READ_INPUT_TOKENS)),
                integerValue(map.get(KEY_CACHE_WRITE_INPUT_TOKENS)),
                completeness(map.get(KEY_COMPLETENESS)));
        return usage.reported() ? usage : null;
    }

    public PromptCacheUsage validatedAgainst(Integer inputTokens) {
        if (completeness != Completeness.COMPLETE || inputTokens == null
                || uncachedInputTokens == null || cacheReadInputTokens == null || cacheWriteInputTokens == null) {
            return this;
        }
        long sum = (long) uncachedInputTokens + cacheReadInputTokens + cacheWriteInputTokens;
        return sum == inputTokens.longValue()
                ? this
                : new PromptCacheUsage(
                        uncachedInputTokens, cacheReadInputTokens, cacheWriteInputTokens, Completeness.PARTIAL);
    }

    public boolean reported() {
        return uncachedInputTokens != null || cacheReadInputTokens != null || cacheWriteInputTokens != null;
    }

    public boolean cacheHit() {
        return cacheReadInputTokens != null && cacheReadInputTokens > 0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(KEY_UNCACHED_INPUT_TOKENS, uncachedInputTokens);
        values.put(KEY_CACHE_READ_INPUT_TOKENS, cacheReadInputTokens);
        values.put(KEY_CACHE_WRITE_INPUT_TOKENS, cacheWriteInputTokens);
        values.put(KEY_COMPLETENESS, completeness.name());
        return ChatMetadataMaps.compact(values);
    }

    private static Completeness completeness(Object value) {
        if (value instanceof Completeness completeness) {
            return completeness;
        }
        if (value != null) {
            try {
                return Completeness.valueOf(value.toString().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return Completeness.PARTIAL;
            }
        }
        return Completeness.PARTIAL;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            long longValue = number.longValue();
            return longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE
                    ? (int) longValue
                    : null;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    public enum Completeness {
        COMPLETE,
        PARTIAL
    }
}
