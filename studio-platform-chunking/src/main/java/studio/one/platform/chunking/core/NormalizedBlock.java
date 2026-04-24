package studio.one.platform.chunking.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser-neutral logical block prepared for chunking.
 */
public record NormalizedBlock(
        String id,
        NormalizedBlockType type,
        String text,
        String sourceRef,
        Integer page,
        Integer slide,
        Integer order,
        String parentBlockId,
        String headingPath,
        java.util.List<String> blockIds,
        Double confidence,
        Map<String, Object> metadata) {

    public static final String KEY_ROW_COUNT = "rowCount";
    public static final String KEY_CELL_COUNT = "cellCount";
    public static final String KEY_HEADING_PATH = "headingPath";
    public static final String KEY_BLOCK_IDS = "blockIds";
    public static final String KEY_CONFIDENCE = "confidence";

    public NormalizedBlock {
        id = normalize(id);
        type = type == null ? NormalizedBlockType.UNKNOWN : type;
        text = text == null ? "" : text.trim();
        sourceRef = normalize(sourceRef);
        parentBlockId = normalize(parentBlockId);
        headingPath = normalize(headingPath);
        blockIds = sanitizeList(blockIds, id, sourceRef);
        metadata = sanitize(metadata);
    }

    public boolean hasText() {
        return !text.isBlank();
    }

    public String effectiveSourceRef() {
        if (!sourceRef.isBlank()) {
            return sourceRef;
        }
        return id;
    }

    public static Builder builder(NormalizedBlockType type, String text) {
        return new Builder(type, text);
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

    private static java.util.List<String> sanitizeList(java.util.List<String> values, String fallbackId, String fallbackSourceRef) {
        java.util.List<String> sanitized = values == null ? java.util.List.of() : values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (!sanitized.isEmpty()) {
            return sanitized;
        }
        if (fallbackSourceRef != null && !fallbackSourceRef.isBlank()) {
            return java.util.List.of(fallbackSourceRef);
        }
        return fallbackId == null || fallbackId.isBlank() ? java.util.List.of() : java.util.List.of(fallbackId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String id;
        private final NormalizedBlockType type;
        private final String text;
        private String sourceRef;
        private Integer page;
        private Integer slide;
        private Integer order;
        private String parentBlockId;
        private String headingPath;
        private java.util.List<String> blockIds = java.util.List.of();
        private Double confidence;
        private Map<String, Object> metadata = Map.of();

        private Builder(NormalizedBlockType type, String text) {
            this.type = type;
            this.text = text;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceRef(String sourceRef) {
            this.sourceRef = sourceRef;
            return this;
        }

        public Builder page(Integer page) {
            this.page = page;
            return this;
        }

        public Builder slide(Integer slide) {
            this.slide = slide;
            return this;
        }

        public Builder order(Integer order) {
            this.order = order;
            return this;
        }

        public Builder parentBlockId(String parentBlockId) {
            this.parentBlockId = parentBlockId;
            return this;
        }

        public Builder headingPath(String headingPath) {
            this.headingPath = headingPath;
            return this;
        }

        public Builder blockIds(java.util.List<String> blockIds) {
            this.blockIds = blockIds;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public NormalizedBlock build() {
            return new NormalizedBlock(id, type, text, sourceRef, page, slide, order, parentBlockId,
                    headingPath, blockIds, confidence, metadata);
        }
    }
}
