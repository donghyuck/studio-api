package studio.one.platform.chunking.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Standard metadata attached to each chunk before vector indexing.
 */
public record ChunkMetadata(
        String sourceDocumentId,
        String parentId,
        String section,
        int order,
        ChunkingStrategyType strategy,
        String objectType,
        String objectId,
        Integer startOffset,
        Integer endOffset,
        Integer tokenCount,
        Integer charCount,
        Map<String, Object> attributes) {

    public static final String KEY_SOURCE_DOCUMENT_ID = "sourceDocumentId";
    public static final String KEY_PARENT_ID = "parentId";
    public static final String KEY_SECTION = "section";
    public static final String KEY_CHUNK_ORDER = "chunkOrder";
    public static final String KEY_STRATEGY = "strategy";
    public static final String KEY_OBJECT_TYPE = "objectType";
    public static final String KEY_OBJECT_ID = "objectId";
    public static final String KEY_START_OFFSET = "startOffset";
    public static final String KEY_END_OFFSET = "endOffset";
    public static final String KEY_TOKEN_COUNT = "tokenCount";
    public static final String KEY_CHAR_COUNT = "charCount";
    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_SOURCE_REFS = "sourceRefs";
    public static final String KEY_BLOCK_TYPE = "blockType";
    public static final String KEY_PAGE = "page";
    public static final String KEY_SLIDE = "slide";
    public static final String KEY_PARENT_BLOCK_ID = "parentBlockId";
    public static final String KEY_HEADING_PATH = "headingPath";
    public static final String KEY_SOURCE_FORMAT = "sourceFormat";
    public static final String KEY_TOKEN_ESTIMATE = "tokenEstimate";
    public static final String KEY_CHUNK_UNIT = "chunkUnit";
    public static final String KEY_MAX_SIZE = "maxSize";
    public static final String KEY_OVERLAP = "overlap";

    public ChunkMetadata {
        if (order < 0) {
            throw new IllegalArgumentException("order must not be negative");
        }
        strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        attributes = sanitize(attributes);
    }

    public static Builder builder(ChunkingStrategyType strategy, int order) {
        return new Builder(strategy, order);
    }

    /**
     * Converts standard metadata and custom attributes into a metadata map.
     * Null and blank values are omitted to avoid leaking meaningless JSON fields.
     * Existing attribute values take precedence over standard metadata keys.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> metadata = new LinkedHashMap<>(attributes);
        putIfPresent(metadata, KEY_SOURCE_DOCUMENT_ID, sourceDocumentId);
        putIfPresent(metadata, KEY_PARENT_ID, parentId);
        putIfPresent(metadata, KEY_SECTION, section);
        metadata.putIfAbsent(KEY_CHUNK_ORDER, order);
        metadata.putIfAbsent(KEY_STRATEGY, strategy.value());
        putIfPresent(metadata, KEY_OBJECT_TYPE, objectType);
        putIfPresent(metadata, KEY_OBJECT_ID, objectId);
        putIfPresent(metadata, KEY_START_OFFSET, startOffset);
        putIfPresent(metadata, KEY_END_OFFSET, endOffset);
        putIfPresent(metadata, KEY_TOKEN_COUNT, tokenCount);
        putIfPresent(metadata, KEY_CHAR_COUNT, charCount);
        return Map.copyOf(metadata);
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        metadata.putIfAbsent(key, value);
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

    public static final class Builder {
        private final ChunkingStrategyType strategy;
        private final int order;
        private String sourceDocumentId;
        private String parentId;
        private String section;
        private String objectType;
        private String objectId;
        private Integer startOffset;
        private Integer endOffset;
        private Integer tokenCount;
        private Integer charCount;
        private Map<String, Object> attributes = Map.of();

        private Builder(ChunkingStrategyType strategy, int order) {
            this.strategy = strategy;
            this.order = order;
        }

        public Builder sourceDocumentId(String sourceDocumentId) {
            this.sourceDocumentId = sourceDocumentId;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder section(String section) {
            this.section = section;
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

        public Builder startOffset(Integer startOffset) {
            this.startOffset = startOffset;
            return this;
        }

        public Builder endOffset(Integer endOffset) {
            this.endOffset = endOffset;
            return this;
        }

        public Builder tokenCount(Integer tokenCount) {
            this.tokenCount = tokenCount;
            return this;
        }

        public Builder charCount(Integer charCount) {
            this.charCount = charCount;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder attribute(String key, Object value) {
            Map<String, Object> merged = new LinkedHashMap<>(attributes);
            merged.put(key, value);
            this.attributes = merged;
            return this;
        }

        public ChunkMetadata build() {
            return new ChunkMetadata(
                    sourceDocumentId,
                    parentId,
                    section,
                    order,
                    strategy,
                    objectType,
                    objectId,
                    startOffset,
                    endOffset,
                    tokenCount,
                    charCount,
                    attributes);
        }
    }
}
