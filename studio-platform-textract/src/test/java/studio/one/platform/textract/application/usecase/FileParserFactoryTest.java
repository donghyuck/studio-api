package studio.one.platform.textract.application.usecase;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.application.usecase.FileParser;
import studio.one.platform.textract.application.usecase.FileParserFactory;
import studio.one.platform.textract.application.usecase.StructuredFileParser;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParsedFile;

class FileParserFactoryTest {

    @Test
    void dispatchesToFirstSupportingParser() {
        FileParser parser = new StaticParser("parsed");
        FileParserFactory factory = new FileParserFactory(List.of(parser));

        assertEquals(parser, factory.getParser("text/plain", "sample.txt"));
    }

    @Test
    void defaultParseStructuredWrapsParseText() {
        FileParser parser = new StaticParser("hello");

        ParsedFile result = parser.parseStructured("hello".getBytes(UTF_8), "text/plain", "sample.txt");

        assertEquals(DocumentFormat.TEXT, result.format());
        assertEquals("hello", result.plainText());
        assertEquals(1, result.blocks().size());
    }

    @Test
    void structuredParserParseReturnsPlainText() {
        StructuredFileParser parser = new StructuredFileParser() {
            @Override
            public boolean supports(String contentType, String filename) {
                return true;
            }

            @Override
            public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) {
                return ParsedFile.textOnly(DocumentFormat.TEXT, "structured", filename);
            }
        };

        assertEquals("structured", parser.parse("ignored".getBytes(UTF_8), "text/plain", "sample.txt"));
    }

    private record StaticParser(String value) implements FileParser {
        @Override
        public boolean supports(String contentType, String filename) {
            return true;
        }

        @Override
        public String parse(byte[] bytes, String contentType, String filename) {
            return value;
        }
    }
}
