package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
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
            List<ExtractedImage> images = new ArrayList<>();
            int order = 0;

            order = appendBodyElements(doc.getBodyElements(), sb, blocks, tables, images, "body", null, order);
            for (int i = 0; i < doc.getHeaderList().size(); i++) {
                order = appendBodyElements(
                        doc.getHeaderList().get(i).getBodyElements(),
                        sb,
                        blocks,
                        tables,
                        images,
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
                        images,
                        "footer[" + i + "]",
                        BlockType.FOOTER,
                        order);
            }
            order = appendFootnotes(doc.getFootnotes(), sb, blocks, images, order);

            String text = cleanText(sb.toString());
            return new ParsedFile(
                    DocumentFormat.DOCX,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
                    List.of(),
                    List.of(),
                    tables,
                    images,
                    false);

        } catch (IOException | RuntimeException e) {
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
            List<ExtractedImage> images,
            String parentPath,
            BlockType containerType,
            int startOrder) {
        int order = startOrder;
        for (int i = 0; i < elements.size(); i++) {
            IBodyElement element = elements.get(i);
            String path = parentPath + "/element[" + i + "]";
            switch (element.getElementType()) {
                case PARAGRAPH -> order += appendParagraph(
                        (XWPFParagraph) element, sb, blocks, images, path, containerType, order);
                case TABLE -> order += appendTable((XWPFTable) element, sb, blocks, tables, images, path, order);
                default -> { /* ignore other elements */ }
            }
        }
        return order;
    }

    private int appendFootnotes(
            List<XWPFFootnote> footnotes,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedImage> images,
            int startOrder) {
        int order = startOrder;
        for (int footnoteIndex = 0; footnoteIndex < footnotes.size(); footnoteIndex++) {
            XWPFFootnote footnote = footnotes.get(footnoteIndex);
            List<XWPFParagraph> paragraphs = footnote.getParagraphs();
            for (int paragraphIndex = 0; paragraphIndex < paragraphs.size(); paragraphIndex++) {
                String path = "footnote[" + footnoteIndex + "]/paragraph[" + paragraphIndex + "]";
                order += appendParagraph(
                        paragraphs.get(paragraphIndex), sb, blocks, images, path, BlockType.FOOTNOTE, order);
            }
        }
        return order;
    }

    private int appendParagraph(
            XWPFParagraph p,
            StringBuilder sb,
            List<ParsedBlock> blocks,
            List<ExtractedImage> images,
            String path,
            BlockType containerType,
            int order) {
        String text = p.getText();
        appendParagraphImages(p, images, path, cleanText(text));
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
            List<ExtractedImage> images,
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
                XWPFTableCell cell = tableCells.get(colIndex);
                String cellText = cleanText(cell.getText());
                appendCellImages(cell, images, path + "/row[" + rowIndex + "]/cell[" + colIndex + "]", cellText);
                String cellSourceRef = path + "/row[" + rowIndex + "]/cell[" + colIndex + "]";
                cells.add(tableCell(rowIndex, colIndex, 1, 1, cellText, cellSourceRef, cells.size(), rowIndex == 0));
                markdownCells.add(cellText == null ? "" : cellText.replace("\n", " "));
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
        }
        String markdown = String.join("\n", markdownRows);
        tables.add(new ExtractedTable(path, markdown, cells, tableMetadata(path, "docx", cells, cells.isEmpty() ? 0 : 1)));
        blocks.add(new ParsedBlock(path, BlockType.TABLE, path, markdown, null, List.of(), blockMetadata(path, order)));
        return 1;
    }

    private void appendCellImages(XWPFTableCell cell, List<ExtractedImage> images, String path, String caption) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        for (int paragraphIndex = 0; paragraphIndex < paragraphs.size(); paragraphIndex++) {
            appendParagraphImages(
                    paragraphs.get(paragraphIndex),
                    images,
                    path + "/paragraph[" + paragraphIndex + "]",
                    caption);
        }
    }

    private void appendParagraphImages(
            XWPFParagraph paragraph,
            List<ExtractedImage> images,
            String path,
            String caption) {
        List<XWPFRun> runs = paragraph.getRuns();
        for (int runIndex = 0; runIndex < runs.size(); runIndex++) {
            List<XWPFPicture> pictures = runs.get(runIndex).getEmbeddedPictures();
            for (int pictureIndex = 0; pictureIndex < pictures.size(); pictureIndex++) {
                String sourceRef = path + "/run[" + runIndex + "]/picture[" + pictureIndex + "]";
                images.add(toExtractedImage(pictures.get(pictureIndex), sourceRef, caption));
            }
        }
    }

    private ExtractedImage toExtractedImage(XWPFPicture picture, String sourceRef, String caption) {
        XWPFPictureData data = picture.getPictureData();
        String filename = data == null ? "" : data.getFileName();
        String contentType = null;
        if (data != null) {
            PictureType type = data.getPictureTypeEnum();
            contentType = type == null ? null : type.getContentType();
        }
        Map<String, Object> metadata = new LinkedHashMap<>(imageMetadata(sourceRef));
        if (data != null) {
            metadata.put(ExtractedImage.KEY_BIN_DATA_REF, filename);
        }
        String description = cleanText(picture.getDescription());
        if (description != null && !description.isBlank()) {
            metadata.put(ExtractedImage.KEY_ALT_TEXT, description);
        }
        if (caption != null && !caption.isBlank()) {
            metadata.put(ExtractedImage.KEY_CAPTION, caption);
        }
        return new ExtractedImage(
                sourceRef,
                contentType,
                filename,
                toPixels(picture.getWidth()),
                toPixels(picture.getDepth()),
                metadata);
    }

    private Integer toPixels(double emu) {
        if (emu <= 0) {
            return null;
        }
        return Math.max(1, (int) Math.round(emu / Units.EMU_PER_PIXEL));
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
