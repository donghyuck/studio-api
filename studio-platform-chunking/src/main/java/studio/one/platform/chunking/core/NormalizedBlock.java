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
        Map<String, Object> metadata) {

    public NormalizedBlock {
        id = normalize(id);
        type = type == null ? NormalizedBlockType.UNKNOWN : type;
        text = text == null ? "" : text.trim();
        sourceRef = normalize(sourceRef);
        parentBlockId = normalize(parentBlockId);
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public NormalizedBlock build() {
            return new NormalizedBlock(id, type, text, sourceRef, page, slide, order, parentBlockId, metadata);
        }
    }
}
