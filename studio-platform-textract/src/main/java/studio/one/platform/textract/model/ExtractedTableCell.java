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

    public ExtractedTableCell {
        rowSpan = Math.max(1, rowSpan);
        colSpan = Math.max(1, colSpan);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
