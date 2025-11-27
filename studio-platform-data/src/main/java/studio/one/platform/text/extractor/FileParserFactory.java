package studio.one.platform.text.extractor;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FileParserFactory {

    private final List<FileParser> parsers;

    public FileParser getParser(String contentType, String filename) {
        return parsers.stream()
                .filter(p -> p.supports(contentType, filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + filename));
    }
}
