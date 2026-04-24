package studio.one.platform.textract.model;

import java.util.List;
import java.util.Map;

/**
 * Table extracted from a source document.
 */
public record ExtractedTable(
        String path,
        String markdown,
        List<ExtractedTableCell> cells,
        Map<String, Object> metadata) {

    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_VECTOR_TEXT = "vectorText";
    public static final String KEY_HEADER_ROW_COUNT = "headerRowCount";

    public ExtractedTable {
        cells = cells == null ? List.of() : List.copyOf(cells);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : path;
    }

    public String format() {
        Object value = metadata.get(KEY_FORMAT);
        return value instanceof String stringValue ? stringValue : "";
    }

    public String vectorText() {
        Object value = metadata.get(KEY_VECTOR_TEXT);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : markdown;
    }

    public int headerRowCount() {
        Object value = metadata.get(KEY_HEADER_ROW_COUNT);
        if (value instanceof Integer integerValue) {
            return Math.max(0, integerValue);
        }
        if (value instanceof Number numberValue) {
            return Math.max(0, numberValue.intValue());
        }
        return 0;
    }

    public int rowCount() {
        return cells.stream()
                .mapToInt(cell -> cell.row() + cell.rowSpan())
                .max()
                .orElse(0);
    }

    public int cellCount() {
        return cells.size();
    }
}
