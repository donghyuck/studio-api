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
    public static final String KEY_CONFIDENCE_AVAILABLE = "confidenceAvailable";

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

    public boolean confidenceAvailable() {
        Object value = metadata.get(KEY_CONFIDENCE_AVAILABLE);
        return value instanceof Boolean booleanValue && booleanValue;
    }
}
