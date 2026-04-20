package studio.one.platform.text.extractor;

import java.util.List;

/**
 * @deprecated since 2026-04-20. Use
 *             {@link studio.one.platform.textract.extractor.FileParserFactory}.
 */
@Deprecated(forRemoval = false)
public class FileParserFactory {

    private final List<FileParser> parsers;
    private final studio.one.platform.textract.extractor.FileParserFactory delegate;

    public FileParserFactory(List<FileParser> parsers) {
        this.parsers = List.copyOf(parsers);
        this.delegate = new studio.one.platform.textract.extractor.FileParserFactory(this.parsers);
    }

    public FileParser getParser(String contentType, String filename) {
        studio.one.platform.textract.extractor.FileParser parser = delegate.getParser(contentType, filename);
        if (parser instanceof FileParser legacyParser) {
            return legacyParser;
        }
        return parsers.stream()
                .filter(p -> p == parser)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + filename));
    }

    public studio.one.platform.textract.extractor.FileParserFactory delegate() {
        return delegate;
    }
}
