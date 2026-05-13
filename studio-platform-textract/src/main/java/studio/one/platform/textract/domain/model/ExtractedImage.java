package studio.one.platform.textract.domain.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Image reference extracted from a source document.
 */
@Value
@Accessors(fluent = true)
public class ExtractedImage {
    String path;
    String contentType;
    String filename;
    Integer width;
    Integer height;
    Map<String, Object> metadata;


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

    public ExtractedImage(String path, String contentType, String filename, Integer width, Integer height, Map<String, Object> metadata) {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        this.path = path;

        this.contentType = contentType;

        this.filename = filename;

        this.width = width;

        this.height = height;

        this.metadata = metadata;

    }

    public String mimeType() {
        return contentType == null ? "" : contentType;
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        if (value instanceof String) {
            String stringValue = (String) value;
            return !stringValue.isBlank() ? stringValue : path;
        }
        return path;
    }

    public List<String> sourceRefs() {
        Object value = metadata.get(KEY_SOURCE_REFS);
        if (value instanceof List<?>) {
            List<?> listValue = (List<?>) value;
            return listValue.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
        }
        String sourceRef = sourceRef();
        return sourceRef.isBlank() ? List.of() : List.of(sourceRef);
    }

    public String binDataRef() {
        Object value = metadata.get(KEY_BIN_DATA_REF);
        return value instanceof String ? (String) value : "";
    }

    public String packageId() {
        Object value = metadata.get(KEY_PACKAGE_ID);
        return value instanceof String ? (String) value : "";
    }

    public String caption() {
        Object value = metadata.get(KEY_CAPTION);
        return value instanceof String ? (String) value : "";
    }

    public String src() {
        Object value = metadata.get(KEY_SRC);
        return value instanceof String ? (String) value : "";
    }

    public String altText() {
        Object value = metadata.get(KEY_ALT_TEXT);
        return value instanceof String ? (String) value : "";
    }

    public String ocrText() {
        Object value = metadata.get(KEY_OCR_TEXT);
        return value instanceof String ? (String) value : "";
    }

    public boolean ocrApplied() {
        Object value = metadata.get(KEY_OCR_APPLIED);
        return value instanceof Boolean && (Boolean) value;
    }

    public String ocrUnit() {
        Object value = metadata.get(KEY_OCR_UNIT);
        return value instanceof String ? (String) value : "";
    }

    public int ocrLineCount() {
        Object value = metadata.get(KEY_OCR_LINE_COUNT);
        if (value instanceof Integer) {
            Integer integerValue = (Integer) value;
            return Math.max(0, integerValue);
        }
        if (value instanceof Number) {
            Number numberValue = (Number) value;
            return Math.max(0, numberValue.intValue());
        }
        return 0;
    }

    public boolean confidenceAvailable() {
        Object value = metadata.get(KEY_CONFIDENCE_AVAILABLE);
        return value instanceof Boolean && (Boolean) value;
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
        return value instanceof String ? (String) value : "";
    }

    public Double confidence() {
        Object value = metadata.get(KEY_CONFIDENCE);
        if (value instanceof Double) {
            Double doubleValue = (Double) value;
            return doubleValue;
        }
        if (value instanceof Number) {
            Number numberValue = (Number) value;
            return numberValue.doubleValue();
        }
        return null;
    }

    private Integer integerValue(String key) {
        Object value = metadata.get(key);
        if (value instanceof Integer) {
            Integer integerValue = (Integer) value;
            return integerValue;
        }
        if (value instanceof Number) {
            Number numberValue = (Number) value;
            return numberValue.intValue();
        }
        return null;
    }
}
