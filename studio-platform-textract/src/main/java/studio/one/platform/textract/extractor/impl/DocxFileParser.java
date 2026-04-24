package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class DocxFileParser extends AbstractFileParser implements StructuredFileParser {

    @Override
    public boolean supports(String contentType, String filename) {

        if (isContentType(contentType, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            return true;
        return hasExtension(filename, ".docx");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename)
            throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                XWPFDocument doc = new XWPFDocument(in)) {

            StringBuilder sb = new StringBuilder();
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ExtractedTable> tables = new ArrayList<>();

            appendBodyElements(doc.getBodyElements(), sb, blocks, tables, "body");
            for (int i = 0; i < doc.getHeaderList().size(); i++) {
                appendBodyElements(doc.getHeaderList().get(i).getBodyElements(), sb, blocks, tables, "header[" + i + "]");
            }
            for (int i = 0; i < doc.getFooterList().size(); i++) {
                appendBodyElements(doc.getFooterList().get(i).getBodyElements(), sb, blocks, tables, "footer[" + i + "]");
            }

            String text = cleanText(sb.toString());
            return new ParsedFile(
                    DocumentFormat.DOCX,
                    text,
                    blocks,
                    metadata(contentType, filename),
                    List.of(),
                    List.of(),
                    tables,
                    List.of(),
                    false);

        } catch (IOException e) {
            throw new FileParseException("Failed to parse DOCX file: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private void appendBodyElements(
            List<IBodyElement> elements,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            String parentPath) {
        for (int i = 0; i < elements.size(); i++) {
            IBodyElement element = elements.get(i);
            String path = parentPath + "/element[" + i + "]";
            switch (element.getElementType()) {
                case PARAGRAPH -> appendParagraph((XWPFParagraph) element, sb, blocks, path);
                case TABLE -> appendTable((XWPFTable) element, sb, blocks, tables, path);
                default -> { /* ignore other elements */ }
            }
        }
    }

    private void appendParagraph(XWPFParagraph p, StringBuilder sb, List<ParsedBlock> blocks, String path) {
        String text = p.getText();
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            sb.append(trimmed).append("\n");
            blocks.add(ParsedBlock.text(path, BlockType.PARAGRAPH, trimmed));
        }
    }

    private void appendTable(
            XWPFTable table,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            String path) {
        List<ExtractedTableCell> cells = new ArrayList<>();
        List<String> markdownRows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRows().get(rowIndex);
            String rowText = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .collect(Collectors.joining(" | "));
            if (!rowText.isBlank()) {
                sb.append(rowText.trim()).append("\n");
            }
            List<XWPFTableCell> tableCells = row.getTableCells();
            List<String> markdownCells = new ArrayList<>();
            for (int colIndex = 0; colIndex < tableCells.size(); colIndex++) {
                String cellText = cleanText(tableCells.get(colIndex).getText());
                cells.add(new ExtractedTableCell(rowIndex, colIndex, 1, 1, cellText, Map.of()));
                markdownCells.add(cellText == null ? "" : cellText.replace("\n", " "));
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
        }
        String markdown = String.join("\n", markdownRows);
        tables.add(new ExtractedTable(path, markdown, cells, tableMetadata(path, "docx")));
        blocks.add(new ParsedBlock(path, BlockType.TABLE, path, markdown, null, List.of(), Map.of()));
    }

    private Map<String, Object> tableMetadata(String path, String format) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(ExtractedTable.KEY_SOURCE_REF, path);
        metadata.put(ExtractedTable.KEY_FORMAT, format);
        return metadata;
    }

    private Map<String, Object> metadata(String contentType, String filename) {
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
}
