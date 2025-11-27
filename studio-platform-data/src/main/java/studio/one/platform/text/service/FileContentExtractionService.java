package studio.one.platform.text.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import lombok.RequiredArgsConstructor;
import studio.one.platform.text.extractor.FileParseException;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.text.extractor.FileParserFactory;

@RequiredArgsConstructor
public class FileContentExtractionService {

    private final FileParserFactory parserFactory;

    public String extractText(String contentType, String filename, File file) throws FileParseException {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parse(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read file: " + filename, e);
        }
    }

    public String extractText(String contentType, String filename, InputStream is) throws FileParseException {
        try {
            byte[] bytes = is.readAllBytes();
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parse(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read input stream for: " + filename, e);
        }
    }
}
