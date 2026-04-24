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

    @SuppressWarnings("unchecked")
    public List<String> sourceRefs() {
        Object value = metadata.get(KEY_SOURCE_REFS);
        if (value instanceof List<?> listValue) {
            return ((List<Object>) listValue).stream()
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
}
