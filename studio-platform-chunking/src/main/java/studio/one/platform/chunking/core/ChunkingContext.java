package studio.one.platform.chunking.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable request context passed to chunking strategies.
 */
public record ChunkingContext(
        String sourceDocumentId,
        String text,
        String contentType,
        String filename,
        String objectType,
        String objectId,
        ChunkingStrategyType strategy,
        int maxSize,
        int overlap,
        ChunkUnit unit,
        Map<String, Object> metadata) {

    public static final int DEFAULT_MAX_SIZE = 800;
    public static final int DEFAULT_OVERLAP = 100;
    /**
     * Sentinel for property-backed chunk size. Use {@link #configuredDefaults(String)}
     * or {@link Builder#useConfiguredMaxSize()} instead of passing this value to
     * {@link Builder#maxSize(int)}.
     */
    public static final int USE_CONFIGURED_MAX_SIZE = 0;
    /**
     * Sentinel for property-backed overlap. Use {@link #configuredDefaults(String)}
     * or {@link Builder#useConfiguredOverlap()} instead of passing this value to
     * {@link Builder#overlap(int)}.
     */
    public static final int USE_CONFIGURED_OVERLAP = -1;

    public ChunkingContext {
        if (isBlank(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must not be negative");
        }
        if (overlap < -1) {
            throw new IllegalArgumentException("overlap must be greater than or equal to -1");
        }
        if (maxSize > 0 && overlap >= maxSize) {
            throw new IllegalArgumentException("overlap must be less than maxSize");
        }
        unit = unit == null ? ChunkUnit.CHARACTER : unit;
        metadata = sanitize(metadata);
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static Builder configuredDefaults(String text) {
        return builder(text).useConfiguredDefaults();
    }

    public Optional<Object> metadataValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadata.get(key));
    }

    private static Map<String, Object> sanitize(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                return;
            }
            if (value instanceof String stringValue && stringValue.isBlank()) {
                return;
            }
            sanitized.put(key, value);
        });
        return Map.copyOf(sanitized);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static final class Builder {
        private final String text;
        private String sourceDocumentId;
        private String contentType;
        private String filename;
        private String objectType;
        private String objectId;
        private ChunkingStrategyType strategy = ChunkingStrategyType.RECURSIVE;
        private int maxSize = DEFAULT_MAX_SIZE;
        private int overlap = DEFAULT_OVERLAP;
        private ChunkUnit unit = ChunkUnit.CHARACTER;
        private Map<String, Object> metadata = Map.of();

        private Builder(String text) {
            this.text = Objects.requireNonNull(text, "text must not be null");
        }

        public Builder sourceDocumentId(String sourceDocumentId) {
            this.sourceDocumentId = sourceDocumentId;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder objectType(String objectType) {
            this.objectType = objectType;
            return this;
        }

        public Builder objectId(String objectId) {
            this.objectId = objectId;
            return this;
        }

        public Builder strategy(ChunkingStrategyType strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder useConfiguredStrategy() {
            this.strategy = null;
            return this;
        }

        public Builder maxSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be greater than zero");
            }
            this.maxSize = maxSize;
            return this;
        }

        public Builder useConfiguredMaxSize() {
            this.maxSize = USE_CONFIGURED_MAX_SIZE;
            return this;
        }

        public Builder overlap(int overlap) {
            if (overlap < 0) {
                throw new IllegalArgumentException("overlap must not be negative");
            }
            this.overlap = overlap;
            return this;
        }

        public Builder useConfiguredOverlap() {
            this.overlap = USE_CONFIGURED_OVERLAP;
            return this;
        }

        public Builder useConfiguredDefaults() {
            return useConfiguredStrategy()
                    .useConfiguredMaxSize()
                    .useConfiguredOverlap();
        }

        public Builder unit(ChunkUnit unit) {
            this.unit = unit;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ChunkingContext build() {
            return new ChunkingContext(
                    sourceDocumentId,
                    text,
                    contentType,
                    filename,
                    objectType,
                    objectId,
                    strategy,
                    maxSize,
                    overlap,
                    unit,
                    metadata);
        }
    }
}
