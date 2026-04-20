package studio.one.platform.textract.extractor;

import java.util.List;
import java.util.Objects;

public class FileParserFactory {

    private final List<? extends FileParser> parsers;

    public FileParserFactory(List<? extends FileParser> parsers) {
        parsers = List.copyOf(Objects.requireNonNull(parsers, "parsers must not be null"));
        this.parsers = parsers;
    }

    public FileParser getParser(String contentType, String filename) {
        return parsers.stream()
                .filter(p -> p.supports(contentType, filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + filename));
    }
}
