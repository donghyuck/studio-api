package studio.one.platform.text.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import studio.one.platform.text.extractor.FileParseException;
import studio.one.platform.text.extractor.FileParser;
import studio.one.platform.text.extractor.FileParserFactory;

class FileContentExtractionServiceTest {

    @Test
    void extractTextParsesBytesWithinLimit() {
        RecordingParser parser = new RecordingParser();
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(parser)),
                4);

        String result = service.extractText("text/plain", "sample.txt", new ByteArrayInputStream("abcd".getBytes(UTF_8)));

        assertEquals("parsed", result);
        assertArrayEquals("abcd".getBytes(UTF_8), parser.lastBytes);
    }

    @Test
    void extractTextRejectsOversizedInputStreams() {
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(new RecordingParser())),
                4);
        InputStream oversized = new InputStream() {
            private int remaining = 5;

            @Override
            public int read() {
                return remaining-- > 0 ? 'a' : -1;
            }
        };

        FileParseException exception = assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "large.txt", oversized));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getType().getStatus());
        assertEquals("error.text.file.too-large", exception.getType().getId());
    }

    @Test
    void extractTextRejectsOversizedFiles() throws Exception {
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(new RecordingParser())),
                4);
        Path temp = Files.createTempFile("large-attachment", ".txt");
        temp.toFile().deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
            raf.setLength(5);
        }

        FileParseException exception = assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "large.txt", temp.toFile()));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getType().getStatus());
        assertEquals("error.text.file.too-large", exception.getType().getId());
    }

    @Test
    void extractTextConvertsTextractExceptionToLegacyException() {
        FileContentExtractionService service = new FileContentExtractionService(
                new studio.one.platform.textract.service.FileContentExtractionService(
                        new studio.one.platform.textract.extractor.FileParserFactory(List.of(new FailingTextractParser())),
                        10));

        FileParseException exception = assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "sample.txt", new ByteArrayInputStream("x".getBytes(UTF_8))));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getType().getStatus());
    }

    @Test
    void delegateOversizedInputPreservesPayloadTooLargeStatus() {
        FileContentExtractionService service = new FileContentExtractionService(
                new studio.one.platform.textract.service.FileContentExtractionService(
                        new studio.one.platform.textract.extractor.FileParserFactory(List.of(new FailingTextractParser())),
                        4));

        FileParseException exception = assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "large.txt",
                        new ByteArrayInputStream("abcde".getBytes(UTF_8))));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getType().getStatus());
        assertEquals("error.text.file.too-large", exception.getType().getId());
    }

    private static final class RecordingParser implements FileParser {
        private byte[] lastBytes;

        @Override
        public boolean supports(String contentType, String filename) {
            return true;
        }

        @Override
        public String parse(byte[] bytes, String contentType, String filename) {
            lastBytes = bytes;
            return "parsed";
        }
    }

    private static final class FailingTextractParser implements studio.one.platform.textract.extractor.FileParser {
        @Override
        public boolean supports(String contentType, String filename) {
            return true;
        }

        @Override
        public String parse(byte[] bytes, String contentType, String filename) {
            throw new studio.one.platform.textract.extractor.FileParseException("failed");
        }
    }
}
