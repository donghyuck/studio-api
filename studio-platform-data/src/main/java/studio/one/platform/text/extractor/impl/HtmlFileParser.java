package studio.one.platform.text.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jsoup.Jsoup;

import studio.one.platform.text.extractor.FileParseException;

public class HtmlFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (isContentType(contentType, "text/html"))
            return true;
        return hasExtension(filename, ".html", ".htm");
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            String text = Jsoup
                    .parse(in, StandardCharsets.UTF_8.name(), "")
                    .text();
            return cleanText(text);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse HTML: " + safeFilename(filename), e);
        }
    }
}
