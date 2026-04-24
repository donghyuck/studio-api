package studio.one.platform.textract.extractor.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParseWarning;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

@Slf4j
public class PdfFileParser extends AbstractFileParser implements StructuredFileParser {

    private static final double REPEATED_BOUNDARY_RATIO = 0.50d;

    @Override
    public boolean supports(String contentType, String filename) {
        try {
            if (contentType != null) {
                MediaType mt = MediaType.parseMediaType(contentType);
                if (MediaType.APPLICATION_PDF.includes(mt)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse media type: {}", contentType, e);
        }
        return hasExtension(filename, ".pdf");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = extractPages(document, stripper);
            List<String> cleanedPages = cleanPdfPages(pages);
            List<ExtractedImage> images = extractImages(document);
            TableExtraction tableExtraction = extractTables(cleanedPages);
            List<ParsedBlock> pageBlocks = new ArrayList<>();
            List<ParsedBlock> blocks = new ArrayList<>();
            int order = 0;
            for (int pageIndex = 0; pageIndex < cleanedPages.size(); pageIndex++) {
                String pageText = cleanedPages.get(pageIndex);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }
                int pageNumber = pageIndex + 1;
                String pagePath = "page[" + pageNumber + "]";
                pageBlocks.add(ParsedBlock.text(
                        pagePath,
                        BlockType.PAGE,
                        pageText,
                        pageNumber,
                        order,
                        blockMetadata(pagePath, order)));
                order++;
                List<String> paragraphs = splitParagraphs(pageText);
                for (int paragraphIndex = 0; paragraphIndex < paragraphs.size(); paragraphIndex++) {
                    String paragraph = paragraphs.get(paragraphIndex);
                    String paragraphPath = pagePath + "/paragraph[" + paragraphIndex + "]";
                    blocks.add(ParsedBlock.text(
                            paragraphPath,
                            BlockType.PARAGRAPH,
                            paragraph,
                            pageNumber,
                            order,
                            blockMetadata(paragraphPath, order)));
                    order++;
                }
            }
            for (ExtractedTable table : tableExtraction.tables()) {
                blocks.add(new ParsedBlock(
                        table.sourceRef(),
                        BlockType.TABLE,
                        table.sourceRef(),
                        table.markdown(),
                        null,
                        List.of(),
                        blockMetadata(table.sourceRef(), order)));
                order++;
            }
            String text = cleanText(cleanedPages.stream()
                    .filter(page -> page != null && !page.isBlank())
                    .collect(Collectors.joining("\n\n")));
            return new ParsedFile(
                    DocumentFormat.PDF,
                    text,
                    blocks,
                    fileMetadata(contentType, filename),
                    tableExtraction.warnings(),
                    pageBlocks,
                    tableExtraction.tables(),
                    images,
                    false);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse PDF file: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private List<String> extractPages(PDDocument document, PDFTextStripper stripper) throws IOException {
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(stripper.getText(document));
        }
        return pages;
    }

    private List<ExtractedImage> extractImages(PDDocument document) throws IOException {
        List<ExtractedImage> images = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            String pagePath = "page[" + (pageIndex + 1) + "]";
            appendImages(page.getResources(), images, pagePath, pagePath);
        }
        return images;
    }

    private void appendImages(
            PDResources resources,
            List<ExtractedImage> images,
            String sourceRef,
            String binDataRefPrefix) throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            String objectRef = sourceRef + "/xobject[" + name.getName() + "]";
            if (xObject instanceof PDImageXObject image) {
                images.add(toExtractedImage(image, objectRef, binDataRefPrefix + "/" + name.getName()));
            } else if (xObject instanceof PDFormXObject form) {
                appendImages(form.getResources(), images, objectRef, binDataRefPrefix + "/" + name.getName());
            }
        }
    }

    private ExtractedImage toExtractedImage(PDImageXObject image, String sourceRef, String binDataRef) {
        String suffix = image.getSuffix();
        String filename = suffix == null || suffix.isBlank()
                ? binDataRef
                : binDataRef + "." + suffix;
        Map<String, Object> metadata = new LinkedHashMap<>(imageMetadata(sourceRef));
        metadata.put(ExtractedImage.KEY_BIN_DATA_REF, binDataRef);
        return new ExtractedImage(
                sourceRef,
                imageContentType(suffix),
                filename,
                image.getWidth(),
                image.getHeight(),
                metadata);
    }

    private String imageContentType(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return null;
        }
        return switch (suffix.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tif", "tiff" -> "image/tiff";
            case "gif" -> "image/gif";
            default -> "image/" + suffix.toLowerCase();
        };
    }

    private TableExtraction extractTables(List<String> pages) {
        List<ExtractedTable> tables = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();
        int tableIndex = 0;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            String page = pages.get(pageIndex);
            if (page == null || page.isBlank()) {
                continue;
            }
            List<List<String>> rows = new ArrayList<>();
            int candidateStartLine = -1;
            List<String> lines = page.lines().toList();
            for (int lineIndex = 0; lineIndex <= lines.size(); lineIndex++) {
                String line = lineIndex < lines.size() ? lines.get(lineIndex) : "";
                List<String> cells = splitTableCells(line);
                if (cells.size() >= 2) {
                    if (rows.isEmpty()) {
                        candidateStartLine = lineIndex;
                    }
                    rows.add(cells);
                    continue;
                }
                tableIndex = flushTableCandidate(
                        tables,
                        warnings,
                        rows,
                        pageIndex + 1,
                        tableIndex,
                        candidateStartLine);
                rows = new ArrayList<>();
                candidateStartLine = -1;
            }
        }
        return new TableExtraction(tables, warnings);
    }

    private int flushTableCandidate(
            List<ExtractedTable> tables,
            List<ParseWarning> warnings,
            List<List<String>> rows,
            int pageNumber,
            int tableIndex,
            int startLine) {
        if (rows.isEmpty()) {
            return tableIndex;
        }
        int columnCount = rows.get(0).size();
        boolean rectangular = rows.size() >= 2
                && columnCount >= 2
                && rows.stream().allMatch(row -> row.size() == columnCount);
        String sourceRef = "page[" + pageNumber + "]/table[" + tableIndex + "]";
        if (!rectangular) {
            warnings.add(ParseWarning.partial(
                    "TABLE_RECONSTRUCTION_PARTIAL",
                    "PDF table candidate could not be reconstructed safely.",
                    sourceRef,
                    Map.of("line", Math.max(0, startLine))));
            return tableIndex + 1;
        }
        List<ExtractedTableCell> cells = new ArrayList<>();
        List<String> markdownRows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            List<String> markdownCells = new ArrayList<>();
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                String cellSourceRef = sourceRef + "/row[" + rowIndex + "]/cell[" + colIndex + "]";
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put(ExtractedTableCell.KEY_SOURCE_REF, cellSourceRef);
                if (rowIndex == 0) {
                    metadata.put(ExtractedTableCell.KEY_HEADER, true);
                }
                cells.add(new ExtractedTableCell(rowIndex, colIndex, 1, 1, row.get(colIndex), metadata));
                markdownCells.add(row.get(colIndex));
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
        }
        String markdown = String.join("\n", markdownRows);
        Map<String, Object> metadata = new LinkedHashMap<>(tableMetadata(sourceRef, "pdf"));
        metadata.put(ExtractedTable.KEY_HEADER_ROW_COUNT, 1);
        metadata.put(ExtractedTable.KEY_VECTOR_TEXT, rows.stream()
                .map(row -> String.join(" ", row))
                .collect(Collectors.joining("\n")));
        tables.add(new ExtractedTable(sourceRef, markdown, cells, metadata));
        return tableIndex + 1;
    }

    private List<String> splitTableCells(String line) {
        String cleaned = cleanText(line);
        if (cleaned == null || cleaned.isBlank()) {
            return List.of();
        }
        String[] cells = cleaned.split("\\s{2,}");
        if (cells.length < 2) {
            return List.of();
        }
        return Arrays.stream(cells)
                .map(String::trim)
                .filter(cell -> !cell.isBlank())
                .toList();
    }

    List<String> cleanPdfPages(List<String> rawPages) {
        if (rawPages == null || rawPages.isEmpty()) {
            return List.of();
        }
        List<List<String>> pageLines = rawPages.stream()
                .map(this::contentLines)
                .toList();
        Map<String, Integer> boundaryFrequency = new LinkedHashMap<>();
        for (List<String> lines : pageLines) {
            for (String boundaryLine : boundaryLines(lines)) {
                boundaryFrequency.merge(normalizeBoundaryLine(boundaryLine), 1, Integer::sum);
            }
        }
        List<String> repeatedBoundaries = boundaryFrequency.entrySet().stream()
                .filter(entry -> isRepeatedBoundary(entry.getValue(), rawPages.size()))
                .map(Map.Entry::getKey)
                .toList();
        List<String> cleaned = new ArrayList<>();
        for (String rawPage : rawPages) {
            String pageWithoutRepeatedBoundaries = removeRepeatedBoundaries(rawPage, repeatedBoundaries);
            cleaned.add(cleanPdfText(pageWithoutRepeatedBoundaries));
        }
        return cleaned;
    }

    private boolean isRepeatedBoundary(int count, int pageCount) {
        return pageCount > 1 && count >= Math.max(2, (int) Math.ceil(pageCount * REPEATED_BOUNDARY_RATIO));
    }

    String cleanPdfText(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null || cleaned.isBlank()) {
            return cleaned;
        }

        String[] lines = cleaned.split("\\n", -1);
        if (!hasFragmentedLines(lines)) {
            return cleaned;
        }

        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        int previousLineLength = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, paragraphs);
                previousLineLength = 0;
                continue;
            }
            appendLine(paragraph, trimmed, previousLineLength);
            previousLineLength = trimmed.length();
        }
        flushParagraph(paragraph, paragraphs);
        return String.join("\n\n", paragraphs).trim();
    }

    private boolean hasFragmentedLines(String[] lines) {
        int contentLineCount = 0;
        int shortLineCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            contentLineCount++;
            if (trimmed.length() <= 3) {
                shortLineCount++;
            }
        }
        return contentLineCount >= 8 && (double) shortLineCount / contentLineCount >= 0.35d;
    }

    private List<String> splitParagraphs(String pageText) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : pageText.split("\\n\\s*\\n")) {
            String cleaned = cleanText(paragraph);
            if (cleaned != null && !cleaned.isBlank()) {
                paragraphs.add(cleaned);
            }
        }
        return paragraphs;
    }

    private List<String> contentLines(String raw) {
        String cleaned = cleanText(raw);
        if (cleaned == null || cleaned.isBlank()) {
            return List.of();
        }
        return cleaned.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private List<String> boundaryLines(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> boundaries = new ArrayList<>();
        boundaries.add(lines.get(0));
        if (lines.size() > 1) {
            boundaries.add(lines.get(lines.size() - 1));
        }
        return boundaries;
    }

    private String removeRepeatedBoundaries(String raw, List<String> repeatedBoundaries) {
        if (repeatedBoundaries.isEmpty()) {
            return raw;
        }
        return contentLines(raw).stream()
                .filter(line -> !repeatedBoundaries.contains(normalizeBoundaryLine(line)))
                .collect(Collectors.joining("\n"));
    }

    private String normalizeBoundaryLine(String line) {
        return line == null ? "" : line.trim().replaceAll("\\s+", " ");
    }

    private void appendLine(StringBuilder paragraph, String line, int previousLineLength) {
        if (paragraph.isEmpty()) {
            paragraph.append(line);
            return;
        }

        String previous = paragraph.toString();
        char previousLast = previous.charAt(previous.length() - 1);
        char nextFirst = line.charAt(0);
        if (isLeadingPunctuation(nextFirst) || isOpeningBracket(previousLast)
                || previousLineLength <= 3 || line.length() <= 3) {
            paragraph.append(line);
            return;
        }

        paragraph.append(' ').append(line);
    }

    private boolean isLeadingPunctuation(char value) {
        return ",.;:!?，。！？)]}".indexOf(value) >= 0;
    }

    private boolean isOpeningBracket(char value) {
        return "([{".indexOf(value) >= 0;
    }

    private void flushParagraph(StringBuilder paragraph, List<String> paragraphs) {
        if (paragraph.isEmpty()) {
            return;
        }
        paragraphs.add(paragraph.toString());
        paragraph.setLength(0);
    }

    private record TableExtraction(List<ExtractedTable> tables, List<ParseWarning> warnings) {
    }
}
