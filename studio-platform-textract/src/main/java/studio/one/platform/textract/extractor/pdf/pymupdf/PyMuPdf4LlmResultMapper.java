package studio.one.platform.textract.extractor.pdf.pymupdf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.impl.AbstractFileParser;
import studio.one.platform.textract.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParseWarning;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class PyMuPdf4LlmResultMapper extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        return false;
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        throw new FileParseException("PyMuPDF4LLM result mapper is not a file parser.");
    }

    public ParsedFile map(PyMuPdf4LlmResponse response, PdfExtractionRequest request) {
        if (response == null) {
            response = new PyMuPdf4LlmResponse(
                    null, null, "", List.of(), List.of(), List.of(), List.of(), Map.of(), List.of(), null, false);
        }

        List<ParsedBlock> pages = mapPages(response.pages());
        List<ParsedBlock> blocks = mapBlocks(response.blocks());
        List<ExtractedTable> tables = mapTables(response.tables());
        appendTableBlocks(blocks, tables);
        List<ExtractedImage> images = mapImages(response.images());
        List<ParseWarning> warnings = mapWarnings(response.warnings());
        String plainText = plainText(response);

        return new ParsedFile(
                DocumentFormat.PDF,
                plainText,
                blocks,
                metadata(response, request),
                warnings,
                pages,
                tables,
                images,
                Boolean.TRUE.equals(response.ocrApplied()));
    }

    private List<ParsedBlock> mapPages(List<PyMuPdf4LlmResponse.Page> responsePages) {
        List<ParsedBlock> pages = new ArrayList<>();
        for (int index = 0; index < responsePages.size(); index++) {
            PyMuPdf4LlmResponse.Page page = responsePages.get(index);
            int pageNumber = page.pageNumber() == null ? index + 1 : page.pageNumber();
            String sourceRef = sourceRef(page.metadata(), "page[" + pageNumber + "]");
            Map<String, Object> metadata = safeMetadata(page.metadata());
            metadata.put("page", pageNumber);
            pages.add(ParsedBlock.text(
                    sourceRef,
                    BlockType.PAGE,
                    page.text(),
                    pageNumber,
                    index,
                    ParsedBlock.metadata(sourceRef, index, null, null, metadata)));
        }
        return pages;
    }

    private List<ParsedBlock> mapBlocks(List<PyMuPdf4LlmResponse.Block> responseBlocks) {
        List<ParsedBlock> blocks = new ArrayList<>();
        for (int index = 0; index < responseBlocks.size(); index++) {
            PyMuPdf4LlmResponse.Block block = responseBlocks.get(index);
            String sourceRef = firstNonBlank(block.sourceRef(), defaultBlockSourceRef(block, index));
            Map<String, Object> metadata = safeMetadata(block.metadata());
            if (block.level() != null) {
                metadata.put("headingLevel", block.level());
            }
            if (!block.bbox().isEmpty()) {
                metadata.put("bbox", block.bbox());
            }
            blocks.add(new ParsedBlock(
                    sourceRef,
                    blockType(block.type()),
                    sourceRef,
                    cleanText(block.text()),
                    block.pageNumber(),
                    List.of(),
                    ParsedBlock.metadata(sourceRef, block.order() == null ? index : block.order(), null, null, metadata)));
        }
        return blocks;
    }

    private void appendTableBlocks(List<ParsedBlock> blocks, List<ExtractedTable> tables) {
        int order = blocks.stream()
                .map(ParsedBlock::order)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;
        for (ExtractedTable table : tables) {
            boolean alreadyPresent = blocks.stream()
                    .anyMatch(block -> table.sourceRef().equals(block.sourceRef()) && block.blockType() == BlockType.TABLE);
            if (alreadyPresent) {
                continue;
            }
            Integer page = integerValue(table.metadata(), "page");
            blocks.add(new ParsedBlock(
                    table.sourceRef(),
                    BlockType.TABLE,
                    table.sourceRef(),
                    table.markdown(),
                    page,
                    List.of(),
                    ParsedBlock.metadata(table.sourceRef(), order++, null, null, table.metadata())));
        }
    }

    private List<ExtractedTable> mapTables(List<PyMuPdf4LlmResponse.Table> responseTables) {
        List<ExtractedTable> tables = new ArrayList<>();
        for (int index = 0; index < responseTables.size(); index++) {
            PyMuPdf4LlmResponse.Table table = responseTables.get(index);
            int pageNumber = table.pageNumber() == null ? 1 : table.pageNumber();
            String sourceRef = firstNonBlank(table.sourceRef(), "page[" + pageNumber + "]/table[" + index + "]");
            List<ExtractedTableCell> cells = tableCells(table, sourceRef);
            String markdown = firstNonBlank(table.markdown(), markdown(table.headers(), table.rows()));
            Map<String, Object> metadata = new LinkedHashMap<>(tableMetadata(sourceRef, "pymupdf4llm", cells,
                    table.headers().isEmpty() ? 0 : 1));
            metadata.put("page", pageNumber);
            if (!isBlank(table.caption())) {
                metadata.put("caption", table.caption());
            }
            if (!table.bbox().isEmpty()) {
                metadata.put("bbox", table.bbox());
            }
            metadata.putAll(safeMetadata(table.metadata()));
            tables.add(new ExtractedTable(sourceRef, markdown, cells, metadata));
        }
        return tables;
    }

    private List<ExtractedTableCell> tableCells(PyMuPdf4LlmResponse.Table table, String sourceRef) {
        List<List<String>> rows = new ArrayList<>();
        if (!table.headers().isEmpty()) {
            rows.add(table.headers());
        }
        rows.addAll(table.rows());
        List<ExtractedTableCell> cells = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                String cellSourceRef = sourceRef + "/row[" + rowIndex + "]/cell[" + colIndex + "]";
                cells.add(tableCell(rowIndex, colIndex, 1, 1, row.get(colIndex), cellSourceRef, cells.size(), rowIndex == 0));
            }
        }
        return cells;
    }

    private String markdown(List<String> headers, List<List<String>> rows) {
        List<String> markdownRows = new ArrayList<>();
        if (!headers.isEmpty()) {
            markdownRows.add("| " + String.join(" | ", headers) + " |");
            markdownRows.add("| " + headers.stream().map(ignored -> "---").collect(Collectors.joining(" | ")) + " |");
        }
        for (List<String> row : rows) {
            markdownRows.add("| " + String.join(" | ", row) + " |");
        }
        return String.join("\n", markdownRows);
    }

    private List<ExtractedImage> mapImages(List<PyMuPdf4LlmResponse.Image> responseImages) {
        List<ExtractedImage> images = new ArrayList<>();
        for (int index = 0; index < responseImages.size(); index++) {
            PyMuPdf4LlmResponse.Image image = responseImages.get(index);
            int pageNumber = image.pageNumber() == null ? 1 : image.pageNumber();
            String sourceRef = firstNonBlank(image.sourceRef(), "page[" + pageNumber + "]/image[" + index + "]");
            Map<String, Object> metadata = safeMetadata(image.metadata());
            metadata.put(ExtractedImage.KEY_SOURCE_REF, sourceRef);
            metadata.put(ExtractedImage.KEY_PAGE, pageNumber);
            if (!isBlank(image.caption())) {
                metadata.put(ExtractedImage.KEY_CAPTION, image.caption());
            }
            if (!isBlank(image.altText())) {
                metadata.put(ExtractedImage.KEY_ALT_TEXT, image.altText());
            }
            if (!isBlank(image.ocrText())) {
                metadata.put(ExtractedImage.KEY_OCR_TEXT, image.ocrText());
            }
            if (image.ocrApplied() != null) {
                metadata.put(ExtractedImage.KEY_OCR_APPLIED, image.ocrApplied());
            }
            if (!image.bbox().isEmpty()) {
                metadata.put("bbox", image.bbox());
            }
            images.add(new ExtractedImage(
                    sourceRef,
                    firstNonBlank(image.mimeType(), "application/octet-stream"),
                    firstNonBlank(image.name(), sourceRef),
                    image.width(),
                    image.height(),
                    metadata));
        }
        return images;
    }

    private List<ParseWarning> mapWarnings(List<PyMuPdf4LlmResponse.Warning> responseWarnings) {
        return responseWarnings.stream()
                .map(warning -> ParseWarning.warning(
                        firstNonBlank(warning.code(), "PYMUPDF4LLM_WARNING"),
                        warning.message(),
                        firstNonBlank(warning.sourceRef(), "document"),
                        safeMetadata(warning.metadata())))
                .toList();
    }

    private Map<String, Object> metadata(PyMuPdf4LlmResponse response, PdfExtractionRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata(request.contentType(), request.filename()));
        metadata.put(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pymupdf4llm");
        metadata.put("markdownAvailable", response.markdown() != null && !response.markdown().isBlank());
        if (response.elapsedMs() != null) {
            metadata.put("elapsedMs", response.elapsedMs());
        }
        metadata.putAll(safeMetadata(response.metadata()));
        return metadata;
    }

    private String plainText(PyMuPdf4LlmResponse response) {
        String markdown = cleanText(response.markdown());
        if (markdown != null && !markdown.isBlank()) {
            return markdown;
        }
        String pages = response.pages().stream()
                .map(PyMuPdf4LlmResponse.Page::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (!pages.isBlank()) {
            return cleanText(pages);
        }
        return cleanText(response.blocks().stream()
                .map(PyMuPdf4LlmResponse.Block::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n\n")));
    }

    private String defaultBlockSourceRef(PyMuPdf4LlmResponse.Block block, int index) {
        int page = block.pageNumber() == null ? 1 : block.pageNumber();
        return "page[" + page + "]/block[" + index + "]";
    }

    private String sourceRef(Map<String, Object> metadata, String fallback) {
        Object value = metadata.get(ParsedBlock.KEY_SOURCE_REF);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private BlockType blockType(String type) {
        if (type == null || type.isBlank()) {
            return BlockType.PARAGRAPH;
        }
        String normalized = type.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TITLE" -> BlockType.TITLE;
            case "HEADING", "HEADER_TEXT" -> BlockType.HEADING;
            case "LIST", "LIST_ITEM" -> BlockType.LIST_ITEM;
            case "TABLE" -> BlockType.TABLE;
            case "IMAGE", "FIGURE" -> BlockType.IMAGE;
            case "IMAGE_CAPTION", "CAPTION" -> BlockType.IMAGE_CAPTION;
            case "OCR", "OCR_TEXT" -> BlockType.OCR_TEXT;
            case "FOOTNOTE" -> BlockType.FOOTNOTE;
            default -> BlockType.PARAGRAPH;
        };
    }

    private Integer integerValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (metadata == null) {
            return safe;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                safe.put(key, value);
            }
        });
        return safe;
    }
}
