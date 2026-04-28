package studio.one.platform.text.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

import studio.one.platform.text.extractor.FileParseException;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.text.extractor.FileParserFactory;
import studio.one.platform.textract.model.DocumentExtractionResult;
import studio.one.platform.textract.model.ParsedFile;

/**
 * @deprecated since 2026-04-20. Use
 *             {@link studio.one.platform.textract.service.FileContentExtractionService}.
 */
@Deprecated(forRemoval = false)
public class FileContentExtractionService {

    private final FileParserFactory parserFactory;
    private final int maxExtractBytes;
    private final studio.one.platform.textract.service.FileContentExtractionService delegate;

    public FileContentExtractionService(FileParserFactory parserFactory) {
        this(parserFactory, 10 * 1024 * 1024);
    }

    public FileContentExtractionService(FileParserFactory parserFactory, int maxExtractBytes) {
        this.parserFactory = Objects.requireNonNull(parserFactory, "parserFactory must not be null");
        if (maxExtractBytes <= 0) {
            throw new IllegalArgumentException("maxExtractBytes must be positive");
        }
        this.maxExtractBytes = maxExtractBytes;
        this.delegate = null;
    }

    public FileContentExtractionService(studio.one.platform.textract.service.FileContentExtractionService delegate) {
        this.parserFactory = null;
        this.maxExtractBytes = 0;
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    public String extractText(String contentType, String filename, File file) throws FileParseException {
        if (delegate == null) {
            return parseStructured(contentType, filename, file).plainText();
        }
        try {
            return delegate.extractText(contentType, filename, file);
        } catch (studio.one.platform.textract.extractor.FileParseException e) {
            throw FileParseException.from(e);
        }
    }

    public String extractText(String contentType, String filename, InputStream is) throws FileParseException {
        if (delegate == null) {
            return parseStructured(contentType, filename, is).plainText();
        }
        try {
            return delegate.extractText(contentType, filename, is);
        } catch (studio.one.platform.textract.extractor.FileParseException e) {
            throw FileParseException.from(e);
        }
    }

    public DocumentExtractionResult extractDocument(String contentType, String filename, File file)
            throws FileParseException {
        return DocumentExtractionResult.from(parseStructured(contentType, filename, file));
    }

    public ParsedFile parseStructured(String contentType, String filename, File file)
            throws FileParseException {
        if (delegate == null) {
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
        try {
            return delegate.parseStructured(contentType, filename, file);
        } catch (studio.one.platform.textract.extractor.FileParseException e) {
            throw FileParseException.from(e);
        }
    }

    public DocumentExtractionResult extractDocument(String contentType, String filename, InputStream is)
            throws FileParseException {
        return DocumentExtractionResult.from(parseStructured(contentType, filename, is));
    }

    public ParsedFile parseStructured(String contentType, String filename, InputStream is)
            throws FileParseException {
        if (delegate == null) {
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
        try {
            return delegate.parseStructured(contentType, filename, is);
        } catch (studio.one.platform.textract.extractor.FileParseException e) {
            throw FileParseException.from(e);
        }
    }

    private void ensureWithinLimit(long size, String filename) {
        if (size > maxExtractBytes) {
            throw FileParseException.from(
                    new studio.one.platform.textract.extractor.FileSizeLimitExceededException(
                            filename, size, maxExtractBytes));
        }
    }
}
