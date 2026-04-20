package studio.one.platform.textract.model;

import java.util.List;
import java.util.Map;

import studio.one.platform.textract.extractor.DocumentFormat;

/**
 * Structured content extracted from a source document.
 *
 * @deprecated since 2026-04-20. Use {@link ParsedFile}.
 */
@Deprecated(forRemoval = false)
public record DocumentExtractionResult(
        DocumentFormat format,
        String text,
        List<ExtractedText> texts,
        List<ExtractedTable> tables,
        List<ExtractedImage> images,
        Map<String, Object> metadata) {

    public DocumentExtractionResult {
        format = format == null ? DocumentFormat.UNKNOWN : format;
        text = text == null ? "" : text;
        texts = texts == null ? List.of() : List.copyOf(texts);
        tables = tables == null ? List.of() : List.copyOf(tables);
        images = images == null ? List.of() : List.copyOf(images);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DocumentExtractionResult textOnly(DocumentFormat format, String text, String filename) {
        Map<String, Object> metadata = filename == null || filename.isBlank()
                ? Map.of()
                : Map.of("filename", filename);
        List<ExtractedText> texts = text == null || text.isBlank()
                ? List.of()
                : List.of(new ExtractedText("document", text, Map.of()));
        return new DocumentExtractionResult(format, text, texts, List.of(), List.of(), metadata);
    }

    public static DocumentExtractionResult from(ParsedFile parsedFile) {
        if (parsedFile == null) {
            return textOnly(DocumentFormat.UNKNOWN, "", null);
        }
        List<ExtractedText> extractedTexts = parsedFile.blocks().stream()
                .filter(block -> block.text() != null && !block.text().isBlank())
                .map(block -> new ExtractedText(block.path(), block.text(), block.metadata()))
                .toList();
        return new DocumentExtractionResult(
                parsedFile.format(),
                parsedFile.plainText(),
                extractedTexts,
                parsedFile.tables(),
                parsedFile.images(),
                parsedFile.metadata());
    }
}
