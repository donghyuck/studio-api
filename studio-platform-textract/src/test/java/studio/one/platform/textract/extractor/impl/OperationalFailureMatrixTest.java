package studio.one.platform.textract.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.model.ParseWarningSeverity;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

class OperationalFailureMatrixTest {

    @Test
    void corruptBinaryFormatsFailFastAsCompleteParseFailures() throws Exception {
        byte[] corrupt = "not a supported document".getBytes(UTF_8);

        assertThrows(FileParseException.class,
                () -> new PdfFileParser().parseStructured(corrupt, "application/pdf", "corrupt.pdf"));
        assertThrows(FileParseException.class,
                () -> new DocxFileParser().parseStructured(corrupt,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "corrupt.docx"));
        assertThrows(FileParseException.class,
                () -> new PptxFileParser().parseStructured(corrupt,
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "corrupt.pptx"));
        assertThrows(FileParseException.class,
                () -> new ImageFileParser("/tmp", "kor+eng").parseStructured(corrupt, "image/png", "corrupt.png"));
        assertThrows(FileParseException.class,
                () -> new HwpHwpxFileParser().parseStructured(corruptHwpxBytes(), "application/hwpx", "corrupt.hwpx"));
    }

    @Test
    void malformedHtmlRemainsBestEffortBecauseJsoupIsTolerant() {
        String malformed = "<html><main><h1>제목<table><tr><td>A";

        ParsedFile parsed = assertDoesNotThrow(
                () -> new HtmlFileParser().parseStructured(malformed.getBytes(UTF_8), "text/html", "malformed.html"));

        assertEquals(DocumentFormat.HTML, parsed.format());
        assertTrue(parsed.plainText().contains("제목"));
    }

    @Test
    void largeHtmlDocumentSmokeCompletesWithinOperationalBound() {
        StringBuilder html = new StringBuilder("<main>");
        for (int i = 0; i < 500; i++) {
            html.append("<p>문단 ").append(i).append("</p>");
        }
        html.append("</main>");

        ParsedFile parsed = assertTimeout(
                Duration.ofSeconds(2),
                () -> new HtmlFileParser().parseStructured(html.toString().getBytes(UTF_8), "text/html", "large.html"));

        assertEquals(500, parsed.blocks().size());
        assertTrue(parsed.plainText().contains("문단 499"));
    }

    @Test
    void partialWarningsRemainStructuredAndNonFatalAcrossFormats() throws Exception {
        ParsedFile hwpx = new HwpHwpxFileParser()
                .parseStructured(hwpxBytesWithMissingSection(), "application/hwpx", "missing-section.hwpx");
        ParsedFile hwp = new HwpHwpxFileParser()
                .parseStructured(hwpEncryptedHeaderOnlyBytes(), "application/x-hwp", "encrypted.hwp");
        ParsedFile ocr = imageParserWithLowConfidenceBlock();

        assertEquals("HWPX_SECTION_MISSING", hwpx.warnings().get(0).canonicalCode());
        assertTrue(hwpx.warnings().get(0).partialParse());
        assertEquals(ParseWarningSeverity.WARNING, hwpx.warnings().get(0).severity());

        assertEquals("HWP_ENCRYPTED", hwp.warnings().get(0).canonicalCode());
        assertEquals(ParseWarningSeverity.ERROR, hwp.warnings().get(0).severity());
        assertFalse(hwp.warnings().get(0).partialParse());

        assertEquals("OCR_LOW_CONFIDENCE", ocr.warnings().get(0).canonicalCode());
        assertEquals(ParseWarningSeverity.WARNING, ocr.warnings().get(0).severity());
        assertFalse(ocr.warnings().get(0).partialParse());
    }

    private ParsedFile imageParserWithLowConfidenceBlock() {
        ImageFileParser parser = new ImageFileParser("/tmp", "kor+eng");
        List<ParsedBlock> blocks = parser.ocrLineBlocks(List.of(
                new ImageFileParser.OcrToken("흐림", 0.42d, new Rectangle(10, 10, 20, 10))));
        return new ParsedFile(
                DocumentFormat.IMAGE,
                "흐림",
                blocks,
                Map.of(),
                parser.ocrWarnings(blocks),
                List.of(),
                List.of(),
                List.of(),
                true);
    }

    private byte[] corruptHwpxBytes() throws Exception {
        return zip(Map.of("Contents/content.hpf", "<not-xml".getBytes(UTF_8)));
    }

    private byte[] hwpxBytesWithMissingSection() throws Exception {
        return zip(Map.of(
                "Contents/content.hpf", """
                        <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
                          <opf:manifest>
                            <opf:item id="section0" href="section0.xml" media-type="application/xml"/>
                          </opf:manifest>
                          <opf:spine><opf:itemref idref="section0"/></opf:spine>
                        </opf:package>
                        """.getBytes(UTF_8)));
    }

    private byte[] hwpEncryptedHeaderOnlyBytes() {
        byte[] header = new byte[256];
        byte[] signature = "HWP Document File".getBytes(UTF_8);
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[35] = 5;
        header[36] = 0x02;
        return hwpOleWithFileHeader(header);
    }

    private byte[] hwpOleWithFileHeader(byte[] header) {
        try (POIFSFileSystem fs = new POIFSFileSystem();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.getRoot().createDocument("FileHeader", new ByteArrayInputStream(header));
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
