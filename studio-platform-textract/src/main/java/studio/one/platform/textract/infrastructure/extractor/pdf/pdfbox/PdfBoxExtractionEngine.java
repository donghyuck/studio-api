package studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.infrastructure.extractor.impl.AbstractFileParser;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngine;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineType;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.ExtractedImage;
import studio.one.platform.textract.domain.model.ExtractedTable;
import studio.one.platform.textract.domain.model.ExtractedTableCell;
import studio.one.platform.textract.domain.model.ParseWarning;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class PdfBoxExtractionEngine extends AbstractFileParser implements PdfExtractionEngine {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxExtractionEngine.class);

    private static final double REPEATED_BOUNDARY_RATIO = 0.50d;
    private static final String KEY_PDF_OCR_FALLBACK = "pdfOcrFallback";
    private static final String KEY_PDF_OCR_RENDERED_PAGES = "pdfOcrRenderedPages";
    private static final String KEY_PDF_OCR_TOTAL_PAGES = "pdfOcrTotalPages";
    private static final String KEY_PDF_OCR_DPI = "pdfOcrDpi";

    private final PdfOcrFallbackOptions ocrFallbackOptions;

    public PdfBoxExtractionEngine() {
        this(PdfOcrFallbackOptions.disabled());
    }

    public PdfBoxExtractionEngine(PdfOcrFallbackOptions ocrFallbackOptions) {
        this.ocrFallbackOptions = ocrFallbackOptions == null
                ? PdfOcrFallbackOptions.disabled()
                : ocrFallbackOptions;
    }

    @Override
    public PdfExtractionEngineType type() {
        return PdfExtractionEngineType.PDFBOX;
    }

    @Override
    public boolean supports(String contentType, String filename) {
        return supports(new PdfExtractionRequest(new byte[0], contentType, filename, PdfExtractionOptions.defaults()));
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return extract(new PdfExtractionRequest(bytes, contentType, filename, PdfExtractionOptions.defaults())).plainText();
    }

    @Override
    public boolean supports(PdfExtractionRequest request) {
        if (!request.options().pdfBoxEnabled()) {
            return false;
        }
        try {
            if (request.contentType() != null) {
                MediaType mt = MediaType.parseMediaType(request.contentType());
                if (MediaType.APPLICATION_PDF.includes(mt)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse media type: {}", request.contentType(), e);
        }
        return hasExtension(request.filename(), ".pdf");
    }

    @Override
    public ParsedFile extract(PdfExtractionRequest request) throws FileParseException {
        try (PDDocument document = Loader.loadPDF(request.bytes())) {
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
                for (ExtractedTable table : tablesOnPage(tableExtraction.tables(), pagePath)) {
                    blocks.add(new ParsedBlock(
                            table.sourceRef(),
                            BlockType.TABLE,
                            table.sourceRef(),
                            table.markdown(),
                            pageNumber,
                            List.of(),
                            blockMetadata(table.sourceRef(), order)));
                    order++;
                }
            }
            String text = cleanText(cleanedPages.stream()
                    .filter(page -> page != null && !page.isBlank())
                    .collect(Collectors.joining("\n\n")));
            if ((request.options().ocrRequired() || text == null || text.isBlank()) && ocrFallbackOptions.enabled()) {
                return extractWithOcrFallback(document, request, images, tableExtraction.warnings());
            }
            Map<String, Object> metadata = pdfFileMetadata(request.contentType(), request.filename());
            metadata.put("ocrRequired", request.options().ocrRequired());
            if (request.options().ocrLanguage() != null) {
                metadata.put("ocrLanguage", request.options().ocrLanguage());
            }
            return new ParsedFile(
                    DocumentFormat.PDF,
                    text,
                    blocks,
                    metadata,
                    tableExtraction.warnings(),
                    pageBlocks,
                    tableExtraction.tables(),
                    images,
                    false);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse PDF file: " + safeFilename(request.filename()), e);
        }
    }

    private ParsedFile extractWithOcrFallback(
            PDDocument document,
            PdfExtractionRequest request,
            List<ExtractedImage> images,
            List<ParseWarning> existingWarnings) throws FileParseException {
        try {
            String ocrLanguage = request.options().ocrLanguage() == null
                    ? ocrFallbackOptions.language()
                    : request.options().ocrLanguage();
            Object tesseract = createTesseract(ocrLanguage);
            Method doOcr = tesseract.getClass().getMethod("doOCR", BufferedImage.class);

            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            int pagesToRender = pagesToRender(totalPages, request.options());
            List<String> pageTexts = new ArrayList<>();
            List<ParsedBlock> pageBlocks = new ArrayList<>();
            List<ParsedBlock> blocks = new ArrayList<>();
            int order = 0;
            for (int pageIndex = 0; pageIndex < pagesToRender; pageIndex++) {
                int pageNumber = pageIndex + 1;
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, ocrFallbackOptions.dpi());
                String pageText = cleanPdfText((String) doOcr.invoke(tesseract, image));
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }
                pageTexts.add(pageText);
                String pagePath = "page[" + pageNumber + "]";
                pageBlocks.add(ParsedBlock.text(
                        pagePath,
                        BlockType.PAGE,
                        pageText,
                        pageNumber,
                        order,
                        ocrBlockMetadata(pagePath, order, pageNumber)));
                order++;
                for (String line : pageText.split("\\n")) {
                    String cleanedLine = cleanText(line);
                    if (cleanedLine == null || cleanedLine.isBlank()) {
                        continue;
                    }
                    String linePath = pagePath + "/ocr/line[" + blocks.size() + "]";
                    blocks.add(ParsedBlock.text(
                            linePath,
                            BlockType.OCR_TEXT,
                            cleanedLine,
                            pageNumber,
                            order,
                            ocrBlockMetadata(linePath, order, pageNumber)));
                    order++;
                }
            }
            String text = cleanText(String.join("\n\n", pageTexts));
            Map<String, Object> metadata = pdfFileMetadata(request.contentType(), request.filename());
            metadata.put("ocrRequired", request.options().ocrRequired());
            metadata.put("ocrLanguage", ocrLanguage);
            metadata.put(KEY_PDF_OCR_FALLBACK, true);
            metadata.put(KEY_PDF_OCR_RENDERED_PAGES, pagesToRender);
            metadata.put(KEY_PDF_OCR_TOTAL_PAGES, totalPages);
            metadata.put(KEY_PDF_OCR_DPI, ocrFallbackOptions.dpi());
            String fallbackMessage = request.options().ocrRequired()
                    ? "PDF OCR was requested; OCR fallback was used."
                    : "PDF text layer was empty; OCR fallback was used.";
            List<ParseWarning> warnings = new ArrayList<>();
            warnings.add(ParseWarning.warning(
                    "PDF_OCR_FALLBACK_APPLIED",
                    fallbackMessage,
                    "document",
                    Map.of(
                            KEY_PDF_OCR_RENDERED_PAGES, pagesToRender,
                            KEY_PDF_OCR_TOTAL_PAGES, totalPages)));
            if (totalPages > pagesToRender) {
                warnings.add(ParseWarning.partial(
                        "PDF_OCR_PAGE_LIMIT_EXCEEDED",
                        "PDF OCR fallback processed only the configured maximum pages.",
                        "document",
                        Map.of(
                                KEY_PDF_OCR_RENDERED_PAGES, pagesToRender,
                                KEY_PDF_OCR_TOTAL_PAGES, totalPages)));
            }
            warnings.addAll(existingWarnings == null ? List.of() : existingWarnings);
            return new ParsedFile(
                    DocumentFormat.PDF,
                    text,
                    blocks,
                    metadata,
                    warnings,
                    pageBlocks,
                    List.of(),
                    images,
                    true);
        } catch (ReflectiveOperationException | IOException e) {
            throw new FileParseException("Failed to OCR PDF file: " + safeFilename(request.filename()), e);
        }
    }

    int pagesToRender(int totalPages, PdfExtractionOptions options) {
        if (totalPages <= 0) {
            return 0;
        }
        if (options != null && options.ocrForceRequested()) {
            return totalPages;
        }
        int maxPages = ocrFallbackOptions.maxPages();
        return maxPages <= 0 ? totalPages : Math.min(totalPages, maxPages);
    }

    private Object createTesseract(String ocrLanguage) throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.sourceforge.tess4j.Tesseract");
        Object tesseract = type.getConstructor().newInstance();
        if (!ocrFallbackOptions.tesseractDataPath().isBlank()) {
            type.getMethod("setDatapath", String.class).invoke(tesseract, ocrFallbackOptions.tesseractDataPath());
        }
        type.getMethod("setLanguage", String.class).invoke(tesseract, ocrLanguage);
        return tesseract;
    }

    private Map<String, Object> ocrBlockMetadata(String sourceRef, int order, int pageNumber) {
        Map<String, Object> metadata = blockMetadata(sourceRef, order);
        metadata.put(ExtractedImage.KEY_OCR_APPLIED, true);
        metadata.put(ExtractedImage.KEY_OCR_UNIT, "line");
        metadata.put("pageNumber", pageNumber);
        return metadata;
    }

    public List<String> cleanPdfPages(List<String> rawPages) {
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

    public String cleanPdfText(String raw) {
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

    private Map<String, Object> pdfFileMetadata(String contentType, String filename) {
        Map<String, Object> metadata = new LinkedHashMap<>(super.fileMetadata(contentType, filename));
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pdfbox");
        return metadata;
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
            DrawnImageCollector collector = new DrawnImageCollector(page, pagePath);
            collector.processPage(page);
            images.addAll(collector.images());
        }
        return images;
    }

    private ExtractedImage toExtractedImage(PDImage image, String sourceRef, String binDataRef) {
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
            default -> null;
        };
    }

    private List<ExtractedTable> tablesOnPage(List<ExtractedTable> tables, String pagePath) {
        return tables.stream()
                .filter(table -> table.sourceRef().startsWith(pagePath + "/"))
                .toList();
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
        if (rows.size() == 1) {
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
                cells.add(tableCell(rowIndex, colIndex, 1, 1, row.get(colIndex), cellSourceRef, cells.size(), rowIndex == 0));
                markdownCells.add(row.get(colIndex));
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
            if (rowIndex == 0) {
                markdownRows.add("| " + row.stream()
                        .map(ignored -> "---")
                        .collect(Collectors.joining(" | ")) + " |");
            }
        }
        String markdown = String.join("\n", markdownRows);
        Map<String, Object> metadata = tableMetadata(sourceRef, "pdf", cells, 1);
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

    private boolean isRepeatedBoundary(int count, int pageCount) {
        return pageCount > 1 && count >= Math.max(2, (int) Math.ceil(pageCount * REPEATED_BOUNDARY_RATIO));
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

    private class DrawnImageCollector extends PDFGraphicsStreamEngine {

        private final String pagePath;
        private final List<ExtractedImage> images = new ArrayList<>();
        private int imageIndex;

        DrawnImageCollector(PDPage page, String pagePath) {
            super(page);
            this.pagePath = pagePath;
        }

        List<ExtractedImage> images() {
            return images;
        }

        @Override
        public void drawImage(PDImage image) {
            String sourceRef = pagePath + "/image[" + imageIndex + "]";
            images.add(toExtractedImage(image, sourceRef, sourceRef));
            imageIndex++;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            // Geometry is not needed for image reference extraction.
        }

        @Override
        public void clip(int windingRule) {
            // No-op.
        }

        @Override
        public void moveTo(float x, float y) {
            // No-op.
        }

        @Override
        public void lineTo(float x, float y) {
            // No-op.
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            // No-op.
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {
            // No-op.
        }

        @Override
        public void endPath() {
            // No-op.
        }

        @Override
        public void strokePath() {
            // No-op.
        }

        @Override
        public void fillPath(int windingRule) {
            // No-op.
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            // No-op.
        }

        @Override
        public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {
            // No-op.
        }
    }
}
