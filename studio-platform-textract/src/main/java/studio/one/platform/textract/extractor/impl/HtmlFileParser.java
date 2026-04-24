package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class HtmlFileParser extends AbstractFileParser implements StructuredFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (isContentType(contentType, "text/html"))
            return true;
        return hasExtension(filename, ".html", ".htm");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            Document document = Jsoup.parse(in, StandardCharsets.UTF_8.name(), "");
            document.select("script, style, noscript, template, nav, aside, footer, form").remove();
            Element root = semanticRoot(document);
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ExtractedTable> tables = new ArrayList<>();
            List<ExtractedImage> images = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            int order = 0;

            for (Element element : root.select("h1, h2, h3, h4, h5, h6, p, li, table, img")) {
                String path = cssPath(element, order);
                if ("table".equals(element.tagName())) {
                    ExtractedTable table = extractTable(element, path);
                    if (!table.markdown().isBlank()) {
                        tables.add(table);
                        blocks.add(ParsedBlock.text(path, BlockType.TABLE, table.markdown(), null, order, blockMetadata(path, order)));
                        sb.append(table.markdown()).append("\n");
                        order++;
                    }
                    continue;
                }
                if ("img".equals(element.tagName())) {
                    ExtractedImage image = extractImage(element, path);
                    images.add(image);
                    String alt = cleanText(element.attr("alt"));
                    if (alt != null && !alt.isBlank()) {
                        blocks.add(ParsedBlock.text(path, BlockType.IMAGE_CAPTION, alt, null, order, blockMetadata(path, order)));
                        sb.append(alt).append("\n");
                        order++;
                    }
                    continue;
                }
                String text = cleanText(element.text());
                if (text == null || text.isBlank() || hasTableAncestor(element)) {
                    continue;
                }
                BlockType type = resolveElementType(element);
                blocks.add(ParsedBlock.text(path, type, text, null, order, blockMetadata(path, order)));
                sb.append(text).append("\n");
                order++;
            }

            String text = cleanText(sb.toString());
            if (text == null || text.isBlank()) {
                text = cleanText(root.text());
            }
            return new ParsedFile(
                    DocumentFormat.HTML,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
                    List.of(),
                    List.of(),
                    tables,
                    images,
                    false);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse HTML: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private Element semanticRoot(Document document) {
        Elements roots = document.select("main, article");
        return roots.isEmpty() ? document.body() : roots.first();
    }

    private BlockType resolveElementType(Element element) {
        String tag = element.tagName();
        if ("h1".equals(tag)) {
            return BlockType.TITLE;
        }
        if (tag.matches("h[2-6]")) {
            return BlockType.HEADING;
        }
        if ("li".equals(tag)) {
            return BlockType.LIST_ITEM;
        }
        return BlockType.PARAGRAPH;
    }

    private ExtractedTable extractTable(Element table, String path) {
        List<ExtractedTableCell> cells = new ArrayList<>();
        List<String> markdownRows = new ArrayList<>();
        Elements rows = table.select("tr");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Elements rowCells = rows.get(rowIndex).select("th, td");
            List<String> markdownCells = new ArrayList<>();
            for (int colIndex = 0; colIndex < rowCells.size(); colIndex++) {
                Element cell = rowCells.get(colIndex);
                String text = cleanText(cell.text());
                int rowSpan = attrInt(cell.attr("rowspan"), 1);
                int colSpan = attrInt(cell.attr("colspan"), 1);
                String cellSourceRef = path + "/row[" + rowIndex + "]/cell[" + colIndex + "]";
                boolean header = "th".equals(cell.tagName()) || rowIndex == 0;
                cells.add(tableCell(rowIndex, colIndex, rowSpan, colSpan, text, cellSourceRef, cells.size(), header));
                markdownCells.add(text == null ? "" : text.replace("\n", " "));
            }
            if (!markdownCells.isEmpty()) {
                markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
            }
        }
        return new ExtractedTable(path, String.join("\n", markdownRows), cells, tableMetadata(path, "html", cells, headerRowCount(cells)));
    }

    private int attrInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int headerRowCount(List<ExtractedTableCell> cells) {
        return cells.stream().anyMatch(ExtractedTableCell::header) ? 1 : 0;
    }

    private ExtractedImage extractImage(Element image, String path) {
        Map<String, Object> metadata = imageMetadata(path);
        String source = image.attr("src");
        if (source != null && !source.isBlank()) {
            metadata.put(ExtractedImage.KEY_SRC, source);
        }
        String alt = image.attr("alt");
        if (alt != null && !alt.isBlank()) {
            metadata.put(ExtractedImage.KEY_ALT_TEXT, alt);
        }
        return new ExtractedImage(path, null, source, null, null, metadata);
    }

    private boolean hasTableAncestor(Element element) {
        return element.parents().stream().anyMatch(parent -> "table".equals(parent.tagName()));
    }

    private String cssPath(Element element, int order) {
        String id = element.id();
        if (id != null && !id.isBlank()) {
            return element.tagName() + "#" + id;
        }
        return element.tagName() + "[" + order + "]";
    }

}
