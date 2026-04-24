package studio.one.platform.textract.model;

import java.util.Map;

/**
 * Cell extracted from a document table.
 */
public record ExtractedTableCell(
        int row,
        int col,
        int rowSpan,
        int colSpan,
        String text,
        Map<String, Object> metadata) {

    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_HEADER = "header";
    public static final String KEY_ORDER = "order";

    public ExtractedTableCell {
        rowSpan = Math.max(1, rowSpan);
        colSpan = Math.max(1, colSpan);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String stringValue ? stringValue : "";
    }

    public boolean header() {
        Object value = metadata.get(KEY_HEADER);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    public Integer order() {
        Object value = metadata.get(KEY_ORDER);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }
}
