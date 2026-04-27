package studio.one.platform.textract.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG-friendly logical content block.
 */
public record ParsedBlock(
        String id,
        BlockType type,
        String path,
        String text,
        Integer page,
        List<ParsedBlock> children,
        Map<String, Object> metadata) {

    public static final String KEY_ORDER = "order";
    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_PARENT_BLOCK_ID = "parentBlockId";
    public static final String KEY_CONFIDENCE = "confidence";
    public static final String KEY_SLIDE = "slide";

    public ParsedBlock {
        id = id == null ? path : id;
        type = type == null ? BlockType.UNKNOWN : type;
        path = path == null ? "" : path;
        text = text == null ? "" : text;
        children = children == null ? List.of() : List.copyOf(children);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ParsedBlock text(String path, BlockType type, String text) {
        return text(path, type, text, null, null, Map.of());
    }

    public static ParsedBlock text(
            String path,
            BlockType type,
            String text,
            Integer page,
            Integer order,
            Map<String, Object> metadata) {
        return new ParsedBlock(path, type, path, text, page, List.of(), metadata(path, order, null, null, metadata));
    }

    public BlockType blockType() {
        return type;
    }

    public Integer order() {
        Object value = metadata.get(KEY_ORDER);
        return value instanceof Integer integerValue ? integerValue : null;
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : path;
    }

    public String parentBlockId() {
        Object value = metadata.get(KEY_PARENT_BLOCK_ID);
        return value instanceof String stringValue ? stringValue : "";
    }

    public Double confidence() {
        Object value = metadata.get(KEY_CONFIDENCE);
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        return null;
    }

    public Integer slide() {
        Object value = metadata.get(KEY_SLIDE);
        return value instanceof Integer integerValue ? integerValue : null;
    }

    public static Map<String, Object> metadata(
            String sourceRef,
            Integer order,
            String parentBlockId,
            Double confidence,
            Map<String, Object> metadata) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        if (sourceRef != null && !sourceRef.isBlank()) {
            merged.putIfAbsent(KEY_SOURCE_REF, sourceRef);
        }
        if (order != null) {
            merged.putIfAbsent(KEY_ORDER, order);
        }
        if (parentBlockId != null && !parentBlockId.isBlank()) {
            merged.putIfAbsent(KEY_PARENT_BLOCK_ID, parentBlockId);
        }
        if (confidence != null) {
            merged.putIfAbsent(KEY_CONFIDENCE, confidence);
        }
        return merged;
    }
}
