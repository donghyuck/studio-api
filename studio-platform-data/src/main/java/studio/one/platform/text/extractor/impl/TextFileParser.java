package studio.one.platform.text.extractor.impl;

import java.nio.charset.StandardCharsets;

import studio.one.platform.text.extractor.FileParseException;

public class TextFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        boolean isText = isContentType(contentType, "text/");
        if (!isText) {
            isText = hasExtension(filename, ".txt", ".log", ".csv");
        }
        return isText;
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}