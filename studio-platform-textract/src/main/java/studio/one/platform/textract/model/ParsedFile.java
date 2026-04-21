package studio.one.platform.textract.model;

import java.util.List;
import java.util.Map;

import studio.one.platform.textract.extractor.DocumentFormat;

/**
 * Structured file parsing result for text extraction and RAG indexing.
 */
public record ParsedFile(
        DocumentFormat format,
        String plainText,
        List<ParsedBlock> blocks,
        Map<String, Object> metadata,
        List<ParseWarning> warnings,
        List<ParsedBlock> pages,
        List<ExtractedTable> tables,
        List<ExtractedImage> images,
        boolean ocrApplied) {

    public ParsedFile {
        format = format == null ? DocumentFormat.UNKNOWN : format;
        plainText = plainText == null ? "" : plainText;
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        pages = pages == null ? List.of() : List.copyOf(pages);
        tables = tables == null ? List.of() : List.copyOf(tables);
        images = images == null ? List.of() : List.copyOf(images);
    }

    public static ParsedFile textOnly(DocumentFormat format, String plainText, String filename) {
        Map<String, Object> metadata = filename == null || filename.isBlank()
                ? Map.of()
                : Map.of("filename", filename);
        List<ParsedBlock> blocks = plainText == null || plainText.isBlank()
                ? List.of()
                : List.of(ParsedBlock.text("document", BlockType.DOCUMENT, plainText, null, 0, Map.of()));
        return new ParsedFile(format, plainText, blocks, metadata, List.of(), List.of(), List.of(), List.of(), false);
    }
}
