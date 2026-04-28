package studio.one.platform.textract.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.extractor.FileParserFactory;
import studio.one.platform.textract.extractor.FileSizeLimitExceededException;
import studio.one.platform.textract.model.DocumentExtractionResult;
import studio.one.platform.textract.model.ParsedFile;

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
        return parseStructured(contentType, filename, file).plainText();
    }

    public ParsedFile parseStructured(String contentType, String filename, File file)
            throws FileParseException {
        try {
            long fileSize = Files.size(file.toPath());
            ensureWithinLimit(fileSize, filename);
            byte[] bytes = Files.readAllBytes(file.toPath());
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parseStructured(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read file: " + filename, e);
        }
    }

    /**
     * @deprecated since 2026-04-20. Use {@link #parseStructured(String, String, File)}.
     */
    @Deprecated(forRemoval = false)
    public DocumentExtractionResult extractDocument(String contentType, String filename, File file)
            throws FileParseException {
        return DocumentExtractionResult.from(parseStructured(contentType, filename, file));
    }

    public String extractText(String contentType, String filename, InputStream is) throws FileParseException {
        return parseStructured(contentType, filename, is).plainText();
    }

    public ParsedFile parseStructured(String contentType, String filename, InputStream is)
            throws FileParseException {
        try {
            int readLimit = maxExtractBytes == Integer.MAX_VALUE ? Integer.MAX_VALUE : maxExtractBytes + 1;
            byte[] bytes = is.readNBytes(readLimit);
            ensureWithinLimit(bytes.length, filename);
            FileParser parser = parserFactory.getParser(contentType, filename);
            return parser.parseStructured(bytes, contentType, filename);
        } catch (IOException e) {
            throw new FileParseException("Failed to read input stream for: " + filename, e);
        }
    }

    /**
     * @deprecated since 2026-04-20. Use {@link #parseStructured(String, String, InputStream)}.
     */
    @Deprecated(forRemoval = false)
    public DocumentExtractionResult extractDocument(String contentType, String filename, InputStream is)
            throws FileParseException {
        return DocumentExtractionResult.from(parseStructured(contentType, filename, is));
    }

    private void ensureWithinLimit(long size, String filename) {
        if (size > maxExtractBytes) {
            throw new FileSizeLimitExceededException(filename, size, maxExtractBytes);
        }
    }
}
