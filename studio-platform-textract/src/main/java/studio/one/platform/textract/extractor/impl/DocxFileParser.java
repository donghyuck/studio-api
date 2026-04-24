package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
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
            int order = 0;

            order = appendBodyElements(doc.getBodyElements(), sb, blocks, tables, "body", null, order);
            for (int i = 0; i < doc.getHeaderList().size(); i++) {
                order = appendBodyElements(
                        doc.getHeaderList().get(i).getBodyElements(),
                        sb,
                        blocks,
                        tables,
                        "header[" + i + "]",
                        BlockType.HEADER,
                        order);
            }
            for (int i = 0; i < doc.getFooterList().size(); i++) {
                order = appendBodyElements(
                        doc.getFooterList().get(i).getBodyElements(),
                        sb,
                        blocks,
                        tables,
                        "footer[" + i + "]",
                        BlockType.FOOTER,
                        order);
            }
            order = appendFootnotes(doc.getFootnotes(), sb, blocks, order);

            String text = cleanText(sb.toString());
            return new ParsedFile(
                    DocumentFormat.DOCX,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
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

    private int appendBodyElements(
            List<IBodyElement> elements,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            String parentPath,
            BlockType containerType,
            int startOrder) {
        int order = startOrder;
        for (int i = 0; i < elements.size(); i++) {
            IBodyElement element = elements.get(i);
            String path = parentPath + "/element[" + i + "]";
            switch (element.getElementType()) {
                case PARAGRAPH -> order += appendParagraph((XWPFParagraph) element, sb, blocks, path, containerType, order);
                case TABLE -> order += appendTable((XWPFTable) element, sb, blocks, tables, path, order);
                default -> { /* ignore other elements */ }
            }
        }
        return order;
    }

    private int appendFootnotes(
            List<XWPFFootnote> footnotes,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            int startOrder) {
        int order = startOrder;
        for (int footnoteIndex = 0; footnoteIndex < footnotes.size(); footnoteIndex++) {
            XWPFFootnote footnote = footnotes.get(footnoteIndex);
            List<XWPFParagraph> paragraphs = footnote.getParagraphs();
            for (int paragraphIndex = 0; paragraphIndex < paragraphs.size(); paragraphIndex++) {
                String path = "footnote[" + footnoteIndex + "]/paragraph[" + paragraphIndex + "]";
                order += appendParagraph(paragraphs.get(paragraphIndex), sb, blocks, path, BlockType.FOOTNOTE, order);
            }
        }
        return order;
    }

    private int appendParagraph(
            XWPFParagraph p,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            String path,
            BlockType containerType,
            int order) {
        String text = p.getText();
        if (text != null && !text.isBlank()) {
            String trimmed = text.trim();
            sb.append(trimmed).append("\n");
            blocks.add(ParsedBlock.text(path, resolveParagraphType(p, containerType), trimmed, null, order, blockMetadata(path)));
            return 1;
        }
        return 0;
    }

    private int appendTable(
            XWPFTable table,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            String path,
            int order) {
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
        blocks.add(new ParsedBlock(path, BlockType.TABLE, path, markdown, null, List.of(), blockMetadata(path, order)));
        return 1;
    }

    private BlockType resolveParagraphType(XWPFParagraph paragraph, BlockType containerType) {
        if (containerType == BlockType.HEADER || containerType == BlockType.FOOTER || containerType == BlockType.FOOTNOTE) {
            return containerType;
        }
        if (paragraph.getNumID() != null) {
            return BlockType.LIST_ITEM;
        }
        String style = paragraph.getStyle();
        if (style == null) {
            return BlockType.PARAGRAPH;
        }
        String normalized = style.trim().toLowerCase();
        if (normalized.contains("title")) {
            return BlockType.TITLE;
        }
        if (normalized.startsWith("heading") || normalized.startsWith("header")) {
            return BlockType.HEADING;
        }
        if (normalized.contains("footnote")) {
            return BlockType.FOOTNOTE;
        }
        if (normalized.contains("list")) {
            return BlockType.LIST_ITEM;
        }
        return BlockType.PARAGRAPH;
    }

}
