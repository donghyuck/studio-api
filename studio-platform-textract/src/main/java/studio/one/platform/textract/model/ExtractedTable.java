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

    public ExtractedTable {
        cells = cells == null ? List.of() : List.copyOf(cells);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
