package studio.one.platform.textract.model;

import java.util.List;
import java.util.Map;

/**
 * Image reference extracted from a source document.
 */
public record ExtractedImage(
        String path,
        String contentType,
        String filename,
        Integer width,
        Integer height,
        Map<String, Object> metadata) {

    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_SOURCE_REFS = "sourceRefs";
    public static final String KEY_BIN_DATA_REF = "binDataRef";
    public static final String KEY_PACKAGE_ID = "packageId";
    public static final String KEY_CAPTION = "caption";
    public static final String KEY_SRC = "src";
    public static final String KEY_ALT_TEXT = "altText";
    public static final String KEY_OCR_TEXT = "ocrText";
    public static final String KEY_OCR_APPLIED = "ocrApplied";
    public static final String KEY_OCR_UNIT = "ocrUnit";
    public static final String KEY_OCR_LINE_COUNT = "ocrLineCount";
    public static final String KEY_CONFIDENCE_AVAILABLE = "confidenceAvailable";
    public static final String KEY_PAGE = "page";
    public static final String KEY_SLIDE = "slide";
    public static final String KEY_ORDER = "order";
    public static final String KEY_PARENT_BLOCK_ID = "parentBlockId";
    public static final String KEY_CONFIDENCE = "confidence";

    public ExtractedImage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String mimeType() {
        return contentType == null ? "" : contentType;
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : path;
    }

    public List<String> sourceRefs() {
        Object value = metadata.get(KEY_SOURCE_REFS);
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        String sourceRef = sourceRef();
        return sourceRef.isBlank() ? List.of() : List.of(sourceRef);
    }

    public String binDataRef() {
        Object value = metadata.get(KEY_BIN_DATA_REF);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String packageId() {
        Object value = metadata.get(KEY_PACKAGE_ID);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String caption() {
        Object value = metadata.get(KEY_CAPTION);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String src() {
        Object value = metadata.get(KEY_SRC);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String altText() {
        Object value = metadata.get(KEY_ALT_TEXT);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String ocrText() {
        Object value = metadata.get(KEY_OCR_TEXT);
        return value instanceof String stringValue ? stringValue : "";
    }

    public boolean ocrApplied() {
        Object value = metadata.get(KEY_OCR_APPLIED);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    public String ocrUnit() {
        Object value = metadata.get(KEY_OCR_UNIT);
        return value instanceof String stringValue ? stringValue : "";
    }

    public int ocrLineCount() {
        Object value = metadata.get(KEY_OCR_LINE_COUNT);
        if (value instanceof Integer integerValue) {
            return Math.max(0, integerValue);
        }
        if (value instanceof Number numberValue) {
            return Math.max(0, numberValue.intValue());
        }
        return 0;
    }

    public boolean confidenceAvailable() {
        Object value = metadata.get(KEY_CONFIDENCE_AVAILABLE);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    public Integer page() {
        return integerValue(KEY_PAGE);
    }

    public Integer slide() {
        return integerValue(KEY_SLIDE);
    }

    public Integer order() {
        return integerValue(KEY_ORDER);
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

    private Integer integerValue(String key) {
        Object value = metadata.get(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }
}
