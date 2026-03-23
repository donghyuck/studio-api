package studio.one.platform.text.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

import studio.one.platform.text.extractor.FileParseException;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.text.extractor.FileParserFactory;

public class FileContentExtractionService {

    private final FileParserFactory parserFactory;
    private final int maxExtractBytes;

    public FileContentExtractionService(FileParserFactory parserFactory) {
        this(parserFactory, 10 * 1024 * 1024);
    }

    public FileContentExtractionService(FileParserFactory parserFactory, int maxExtractBytes) {
        this.parserFactory = Objects.requireNonNull(parserFactory);
        if (maxExtractBytes <= 0) {
            throw new IllegalArgumentException("maxExtractBytes must be positive");
        }
        this.maxExtractBytes = maxExtractBytes;
    }

    public String extractText(String contentType, String filename, File file) throws FileParseException {
        try {
            long fileSize = Files.size(file.toPath());
            ensureWithinLimit(fileSize, filename);
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parse(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read file: " + filename, e);
        }
    }

    public String extractText(String contentType, String filename, InputStream is) throws FileParseException {
        try {
            int readLimit = maxExtractBytes == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxExtractBytes + 1;
            byte[] bytes = is.readNBytes(readLimit);
            ensureWithinLimit(bytes.length, filename);
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parse(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read input stream for: " + filename, e);
        }
    }

    private void ensureWithinLimit(long size, String filename) {
        if (size > maxExtractBytes) {
            throw new FileParseException("File too large to extract text: " + filename);
        }
    }
}
