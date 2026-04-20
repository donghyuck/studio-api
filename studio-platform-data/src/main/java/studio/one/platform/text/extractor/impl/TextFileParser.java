package studio.one.platform.text.extractor.impl;

import studio.one.platform.text.extractor.FileParseException;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.textract.model.DocumentExtractionResult;
import studio.one.platform.textract.model.ParsedFile;

/**
 * @deprecated since 2026-04-20. Use
 *             {@link studio.one.platform.textract.extractor.impl.TextFileParser}.
 */
@Deprecated(forRemoval = false)
public class TextFileParser
        extends studio.one.platform.textract.extractor.impl.TextFileParser
        implements FileParser {

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return LegacyParserSupport.translate(() -> super.parse(bytes, contentType, filename));
    }

    @Override
    public DocumentExtractionResult extract(byte[] bytes, String contentType, String filename)
            throws FileParseException {
        return LegacyParserSupport.translate(() -> super.extract(bytes, contentType, filename));
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        return FileParser.super.parseStructured(bytes, contentType, filename);
    }
}
