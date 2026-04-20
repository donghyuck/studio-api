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

import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.FileParser;
import studio.one.platform.textract.extractor.FileParserFactory;

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

        assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "large.txt", oversized));
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

        assertThrows(FileParseException.class,
                () -> service.extractText("text/plain", "large.txt", temp.toFile()));
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
}
