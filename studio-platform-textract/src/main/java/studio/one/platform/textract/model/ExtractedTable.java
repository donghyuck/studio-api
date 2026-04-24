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
