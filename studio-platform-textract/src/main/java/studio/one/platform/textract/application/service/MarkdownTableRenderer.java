package studio.one.platform.textract.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ExtractedTableCell;

public class MarkdownTableRenderer {

    private final MarkdownSanitizer sanitizer;

    public MarkdownTableRenderer(MarkdownSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public String render(ExtractedTable table) {
        if (table == null) {
            return "";
        }
        if (table.markdown() != null && !table.markdown().isBlank()) {
            return sanitizer.sanitize(table.markdown());
        }
        if (table.cells().isEmpty()) {
            return "";
        }

        int rowCount = table.rowCount();
        int columnCount = table.cells().stream()
                .mapToInt(cell -> cell.col() + cell.colSpan())
                .max()
                .orElse(0);
        if (rowCount == 0 || columnCount == 0) {
            return "";
        }

        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < rowCount; row++) {
            rows.add(new ArrayList<>(java.util.Collections.nCopies(columnCount, "")));
        }
        table.cells().stream()
                .sorted(Comparator.comparingInt(ExtractedTableCell::row)
                        .thenComparingInt(ExtractedTableCell::col))
                .forEach(cell -> rows.get(cell.row()).set(cell.col(), sanitizer.inline(cell.text())));

        List<String> lines = new ArrayList<>();
        lines.add(row(rows.get(0)));
        lines.add("| " + String.join(" | ", java.util.Collections.nCopies(columnCount, "---")) + " |");
        for (int index = 1; index < rows.size(); index++) {
            lines.add(row(rows.get(index)));
        }
        return String.join("\n", lines);
    }

    private String row(List<String> cells) {
        return "| " + String.join(" | ", cells) + " |";
    }
}
