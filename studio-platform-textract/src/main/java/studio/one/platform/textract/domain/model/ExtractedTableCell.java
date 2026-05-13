package studio.one.platform.textract.domain.model;

import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Cell extracted from a document table.
 */
@Value
@Accessors(fluent = true)
public class ExtractedTableCell {
    int row;
    int col;
    int rowSpan;
    int colSpan;
    String text;
    Map<String, Object> metadata;


    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_HEADER = "header";
    public static final String KEY_ORDER = "order";

    public ExtractedTableCell(int row, int col, int rowSpan, int colSpan, String text, Map<String, Object> metadata) {
        rowSpan = Math.max(1, rowSpan);
        colSpan = Math.max(1, colSpan);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        this.row = row;

        this.col = col;

        this.rowSpan = rowSpan;

        this.colSpan = colSpan;

        this.text = text;

        this.metadata = metadata;

    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String ? (String) value : "";
    }

    public boolean header() {
        Object value = metadata.get(KEY_HEADER);
        return value instanceof Boolean && (Boolean) value;
    }

    public Integer order() {
        Object value = metadata.get(KEY_ORDER);
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
