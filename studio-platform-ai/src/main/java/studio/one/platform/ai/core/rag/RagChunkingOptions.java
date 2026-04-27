package studio.one.platform.ai.core.rag;

import java.util.Locale;
import java.util.Set;

/**
 * Optional per-request chunking overrides for RAG indexing.
 */
public record RagChunkingOptions(
        String strategy,
        Integer maxSize,
        Integer overlap,
        String unit) {

    private static final Set<String> SUPPORTED_STRATEGIES = Set.of("fixed-size", "recursive", "structure-based");
    private static final Set<String> SUPPORTED_UNITS = Set.of("character", "token");

    public RagChunkingOptions {
        strategy = normalizeStrategy(strategy);
        unit = normalizeUnit(unit);
        if (maxSize != null && maxSize <= 0) {
            throw new IllegalArgumentException("chunk maxSize must be greater than zero");
        }
        if (overlap != null && overlap < 0) {
            throw new IllegalArgumentException("chunk overlap must not be negative");
        }
        if (maxSize != null && overlap != null && overlap >= maxSize) {
            throw new IllegalArgumentException("chunk overlap must be less than maxSize");
        }
    }

    public static RagChunkingOptions empty() {
        return new RagChunkingOptions(null, null, null, null);
    }

    public boolean isEmpty() {
        return strategy == null
                && maxSize == null
                && overlap == null
                && unit == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeStrategy(String value) {
        String normalized = normalizeIdentifier(value);
        if (normalized != null && !SUPPORTED_STRATEGIES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported chunking strategy: " + value
                            + ". Supported values are fixed-size, recursive, and structure-based.");
        }
        return normalized;
    }

    private static String normalizeUnit(String value) {
        String normalized = normalizeIdentifier(value);
        if (normalized != null && !SUPPORTED_UNITS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "unsupported chunk unit: " + value + ". Supported values are character and token.");
        }
        return normalized;
    }

    private static String normalizeIdentifier(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
