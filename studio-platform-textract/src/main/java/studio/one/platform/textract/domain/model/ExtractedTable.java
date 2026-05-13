package studio.one.platform.textract.domain.model;

import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Table extracted from a source document.
 */
@Value
@Accessors(fluent = true)
public class ExtractedTable {
    String path;
    String markdown;
    List<ExtractedTableCell> cells;
    Map<String, Object> metadata;


    public static final String KEY_SOURCE_REF = "sourceRef";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_VECTOR_TEXT = "vectorText";
    public static final String KEY_HEADER_ROW_COUNT = "headerRowCount";

    public ExtractedTable(String path, String markdown, List<ExtractedTableCell> cells, Map<String, Object> metadata) {
        cells = cells == null ? List.of() : List.copyOf(cells);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);

        this.path = path;

        this.markdown = markdown;

        this.cells = cells;

        this.metadata = metadata;

    }

    public String sourceRef() {
        Object value = metadata.get(KEY_SOURCE_REF);
        if (value instanceof String) {
            String stringValue = (String) value;
            return !stringValue.isBlank() ? stringValue : path;
        }
        return path;
    }

    public String format() {
        Object value = metadata.get(KEY_FORMAT);
        return value instanceof String ? (String) value : "";
    }

    public String vectorText() {
        Object value = metadata.get(KEY_VECTOR_TEXT);
        if (value instanceof String) {
            String stringValue = (String) value;
            return !stringValue.isBlank() ? stringValue : markdown;
        }
        return markdown;
    }

    public int headerRowCount() {
        Object value = metadata.get(KEY_HEADER_ROW_COUNT);
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
