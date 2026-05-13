package studio.one.platform.textract.domain.model;

import java.util.List;
import java.util.Map;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Structured file parsing result for text extraction and RAG indexing.
 */
@Value
@Accessors(fluent = true)
public class ParsedFile {
    DocumentFormat format;
    String plainText;
    List<ParsedBlock> blocks;
    Map<String, Object> metadata;
    List<ParseWarning> warnings;
    List<ParsedBlock> pages;
    List<ExtractedTable> tables;
    List<ExtractedImage> images;
    boolean ocrApplied;

    public ParsedFile(
            DocumentFormat format,
            String plainText,
            List<ParsedBlock> blocks,
            Map<String, Object> metadata,
            List<ParseWarning> warnings,
            List<ParsedBlock> pages,
            List<ExtractedTable> tables,
            List<ExtractedImage> images,
            boolean ocrApplied) {
        this.format = format == null ? DocumentFormat.UNKNOWN : format;
        this.plainText = plainText == null ? "" : plainText;
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.pages = pages == null ? List.of() : List.copyOf(pages);
        this.tables = tables == null ? List.of() : List.copyOf(tables);
        this.images = images == null ? List.of() : List.copyOf(images);
        this.ocrApplied = ocrApplied;
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
