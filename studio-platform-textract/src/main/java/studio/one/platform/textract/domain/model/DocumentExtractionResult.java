package studio.one.platform.textract.domain.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import studio.one.platform.textract.domain.model.DocumentFormat;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Structured content extracted from a source document.
 *
 * @deprecated since 2026-04-20. Use {@link ParsedFile}.
 */
@Deprecated(forRemoval = false)
@Value
@Accessors(fluent = true)
public class DocumentExtractionResult {
    DocumentFormat format;
    String text;
    List<ExtractedText> texts;
    List<ExtractedTable> tables;
    List<ExtractedImage> images;
    Map<String, Object> metadata;


    public DocumentExtractionResult(DocumentFormat format, String text, List<ExtractedText> texts, List<ExtractedTable> tables, List<ExtractedImage> images, Map<String, Object> metadata) {
        format = format == null ? DocumentFormat.UNKNOWN : format;
        text = text == null ? "" : text;
        texts = texts == null ? List.of() : List.copyOf(texts);
        tables = tables == null ? List.of() : List.copyOf(tables);
        images = images == null ? List.of() : List.copyOf(images);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);


        this.format = format;


        this.text = text;


        this.texts = texts;


        this.tables = tables;


        this.images = images;


        this.metadata = metadata;


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
                .collect(Collectors.toList());
        return new DocumentExtractionResult(
                parsedFile.format(),
                parsedFile.plainText(),
                extractedTexts,
                parsedFile.tables(),
                parsedFile.images(),
                parsedFile.metadata());
    }
}
