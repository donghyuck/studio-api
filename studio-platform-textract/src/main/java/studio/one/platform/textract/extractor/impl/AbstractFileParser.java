package studio.one.platform.textract.extractor.impl;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedBlock;

@Slf4j
public abstract class AbstractFileParser implements FileParser {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    protected String safeFilename(String filename) {
        return filename != null ? filename : "unknown";
    }

    protected String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    protected boolean hasExtension(String filename, String... exts) {
        if (filename == null)
            return false;
        String lower = lower(filename);
        for (String ext : exts) {
            log.debug("filename={}, ext={} -> {}", lower, ext, lower.endsWith(ext));
            if (lower.endsWith(ext))
                return true;
        }
        return false;
    }

    protected boolean isContentType(String contentType, String... types) {
        if (contentType == null)
            return false;
        String lower = lower(contentType);
        for (String t : types) {
            if (lower.startsWith(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 기본적인 정제: 제어문자 제거, CRLF 정규화, 과도한 공백 줄 축소.
     * 한글/다국어 텍스트는 그대로 유지한다.
     */
    protected String cleanText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String normalized = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");
        normalized = MULTI_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.trim();
    }

    protected Map<String, Object> fileMetadata(String contentType, String filename) {
        if (filename == null || filename.isBlank()) {
            return contentType == null || contentType.isBlank()
                    ? Map.of()
                    : Map.of("contentType", contentType);
        }
        if (contentType == null || contentType.isBlank()) {
            return Map.of("filename", filename);
        }
        return Map.of("filename", filename, "contentType", contentType);
    }

    protected Map<String, Object> blockMetadata(String sourceRef) {
        return blockMetadata(sourceRef, null);
    }

    protected Map<String, Object> blockMetadata(String sourceRef, Integer order) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ParsedBlock.KEY_SOURCE_REF, sourceRef);
        if (order != null) {
            metadata.put(ParsedBlock.KEY_ORDER, order);
        }
        return metadata;
    }

    protected Map<String, Object> tableMetadata(String sourceRef, String format) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedTable.KEY_SOURCE_REF, sourceRef);
        metadata.put(ExtractedTable.KEY_FORMAT, format);
        return metadata;
    }

    protected Map<String, Object> tableMetadata(
            String sourceRef,
            String format,
            List<ExtractedTableCell> cells,
            int headerRowCount) {
        Map<String, Object> metadata = tableMetadata(sourceRef, format);
        metadata.put(ExtractedTable.KEY_HEADER_ROW_COUNT, Math.max(0, headerRowCount));
        metadata.put(ExtractedTable.KEY_VECTOR_TEXT, tableVectorText(cells, headerRowCount));
        return metadata;
    }

    protected ExtractedTableCell tableCell(
            int row,
            int col,
            int rowSpan,
            int colSpan,
            String text,
            String sourceRef,
            int order,
            boolean header) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedTableCell.KEY_SOURCE_REF, sourceRef);
        metadata.put(ExtractedTableCell.KEY_ORDER, order);
        if (header) {
            metadata.put(ExtractedTableCell.KEY_HEADER, true);
        }
        return new ExtractedTableCell(row, col, rowSpan, colSpan, text, metadata);
    }

    private String tableVectorText(List<ExtractedTableCell> cells, int headerRowCount) {
        if (cells == null || cells.isEmpty()) {
            return "";
        }
        Map<Integer, String> headers = cells.stream()
                .filter(cell -> cell.row() < headerRowCount)
                .collect(Collectors.toMap(
                        ExtractedTableCell::col,
                        cell -> value(cell.text()),
                        (left, right) -> left.isBlank() ? right : left,
                        LinkedHashMap::new));
        return cells.stream()
                .collect(Collectors.groupingBy(
                        ExtractedTableCell::row,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> tableVectorRow(entry.getValue(), headers, entry.getKey() >= headerRowCount))
                .filter(row -> !row.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private String tableVectorRow(List<ExtractedTableCell> rowCells, Map<Integer, String> headers, boolean dataRow) {
        return rowCells.stream()
                .sorted(Comparator.comparingInt(ExtractedTableCell::col))
                .map(cell -> tableVectorCell(cell, headers, dataRow))
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private String tableVectorCell(ExtractedTableCell cell, Map<Integer, String> headers, boolean dataRow) {
        String text = value(cell.text()).replace('\n', ' ').trim();
        if (!dataRow || headers.isEmpty()) {
            return text;
        }
        String header = headers.getOrDefault(cell.col(), "");
        return header.isBlank() ? text : header + ": " + text;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    protected Map<String, Object> imageMetadata(String sourceRef) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedImage.KEY_SOURCE_REF, sourceRef);
        return metadata;
    }

    /**
     * 지원 여부를 디버깅할 때 사용. DEBUG 레벨에서만 출력된다.
     */
    protected void debugSupports(String parserName, String contentType, String filename, boolean result) {
        if (log.isDebugEnabled()) {
            log.debug("Parser={} supports? {} (contentType='{}', filename='{}')",
                    parserName, result, contentType, filename);
        }
    }
}
