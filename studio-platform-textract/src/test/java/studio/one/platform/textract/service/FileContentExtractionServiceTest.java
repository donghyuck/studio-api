package studio.one.platform.textract.service;

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

import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.extractor.FileParserFactory;
import studio.one.platform.textract.extractor.FileSizeLimitExceededException;

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

        FileSizeLimitExceededException exception = assertThrows(FileSizeLimitExceededException.class,
                () -> service.extractText("text/plain", "large.txt", oversized));
        assertEquals("File too large to extract text: large.txt "
                + "(observed-size=5 bytes, limit=4 bytes; configure studio.textract.max-extract-size)",
                exception.getMessage());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getType().getStatus());
    }

    @Test
    void parseStructuredRejectsOversizedInputStreamsBeforeParserDispatch() {
        RecordingParser parser = new RecordingParser();
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(parser)),
                4);
        InputStream oversized = new ByteArrayInputStream("abcde".getBytes(UTF_8));

        assertThrows(FileParseException.class,
                () -> service.parseStructured("text/plain", "large.txt", oversized));
        assertEquals(0, parser.invocations);
    }

    @Test
    void extractTextRejectsOversizedFiles() throws Exception {
        RecordingParser parser = new RecordingParser();
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(parser)),
                4);
        Path temp = Files.createTempFile("large-attachment", ".txt");
        temp.toFile().deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
            raf.setLength(5);
        }

        FileSizeLimitExceededException exception = assertThrows(FileSizeLimitExceededException.class,
                () -> service.extractText("text/plain", "large.txt", temp.toFile()));
        assertEquals("File too large to extract text: large.txt "
                + "(observed-size=5 bytes, limit=4 bytes; configure studio.textract.max-extract-size)",
                exception.getMessage());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getType().getStatus());
        assertEquals(0, parser.invocations);
    }

    @Test
    void parseStructuredAcceptsFilesAtExactLimitAndRejectsOversizedFilesBeforeParserDispatch() throws Exception {
        RecordingParser parser = new RecordingParser();
        FileContentExtractionService service = new FileContentExtractionService(
                new FileParserFactory(List.of(parser)),
                4);
        Path exact = Files.createTempFile("exact-attachment", ".txt");
        Path oversized = Files.createTempFile("oversized-attachment", ".txt");
        exact.toFile().deleteOnExit();
        oversized.toFile().deleteOnExit();
        Files.writeString(exact, "abcd", UTF_8);
        Files.writeString(oversized, "abcde", UTF_8);

        service.parseStructured("text/plain", "exact.txt", exact.toFile());
        assertEquals(1, parser.invocations);

        assertThrows(FileParseException.class,
                () -> service.parseStructured("text/plain", "large.txt", oversized.toFile()));
        assertEquals(1, parser.invocations);
    }

    private static final class RecordingParser implements FileParser {
        private byte[] lastBytes;
        private int invocations;

        @Override
        public boolean supports(String contentType, String filename) {
            return true;
        }

        @Override
        public String parse(byte[] bytes, String contentType, String filename) {
            invocations++;
            lastBytes = bytes;
            return "parsed";
        }
    }
}
