package studio.one.platform.textract.infrastructure.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import studio.one.platform.textract.application.service.DocumentFormatDetector;
import studio.one.platform.textract.application.usecase.FileContentExtractionService;
import studio.one.platform.textract.application.usecase.FileParserFactory;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParsedFile;

class EpubFileParserTest {

    @Test
    void extractsSpineDocumentsInReadingOrderAndBuildsMarkdown() throws Exception {
        Map<String, byte[]> entries = baseEntries("""
                <manifest>
                  <item id="chapter-two" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
                  <item id="chapter-one" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                  <itemref idref="chapter-one"/>
                  <itemref idref="chapter-two"/>
                </spine>
                """);
        entries.put("OPS/text/chapter1.xhtml", xhtml("첫 장", "첫 번째 본문", "항목 하나"));
        entries.put("OPS/text/chapter2.xhtml", xhtml("둘째 장", "두 번째 본문", "항목 둘"));
        FileContentExtractionService service =
                new FileContentExtractionService(new FileParserFactory(List.of(new EpubFileParser())));

        ParsedFile result = service.parseStructured(
                "application/epub+zip", "sample.epub", new ByteArrayInputStream(epub(entries)));

        assertThat(result.format()).isEqualTo(DocumentFormat.EPUB);
        assertThat(result.plainText()).containsSubsequence("첫 장", "첫 번째 본문", "둘째 장", "두 번째 본문");
        assertThat(result.markdown()).contains("# 첫 장", "- 항목 하나", "# 둘째 장", "- 항목 둘");
        assertThat(result.contentFormat()).isEqualTo("markdown");
        assertThat(result.blocks()).anyMatch(block -> block.blockType() == BlockType.TITLE);
        assertThat(result.locators()).isNotEmpty();
        assertThat(result.metadata()).containsEntry("contentDocumentCount", 2);
    }

    @Test
    void fallsBackToManifestOrderWhenSpineIsEmpty() throws Exception {
        Map<String, byte[]> entries = baseEntries("""
                <manifest>
                  <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine/>
                """);
        entries.put("OPS/chapter.xhtml", xhtml("제목", "본문", ""));

        ParsedFile result = new EpubFileParser().parseStructured(
                epub(entries), "application/octet-stream", "sample.epub");

        assertThat(result.plainText()).contains("제목", "본문");
    }

    @Test
    void rejectsZipEntryPathTraversal() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../outside.xhtml", "bad".getBytes(UTF_8));

        assertThatThrownBy(() -> new EpubFileParser().parseStructured(
                epub(entries), "application/epub+zip", "bad.epub"))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("escapes package root");
    }

    @Test
    void rejectsManifestHrefOutsidePackageRoot() throws Exception {
        Map<String, byte[]> entries = baseEntries("""
                <manifest>
                  <item id="chapter" href="../../outside.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="chapter"/></spine>
                """);

        assertThatThrownBy(() -> new EpubFileParser().parseStructured(
                epub(entries), "application/epub+zip", "bad.epub"))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("escapes package root");
    }

    @Test
    void rejectsDoctypeAndExternalEntity() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", """
                <!DOCTYPE container [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="&xxe;"/></rootfiles>
                </container>
                """.getBytes(UTF_8));

        assertThatThrownBy(() -> new EpubFileParser().parseStructured(
                epub(entries), "application/epub+zip", "bad.epub"))
                .isInstanceOf(FileParseException.class)
                .hasMessageContaining("Failed to parse EPUB XML");
    }

    @Test
    void detectsEpubByMimeTypeAndExtension() {
        assertThat(DocumentFormatDetector.detect("application/epub+zip", "book.bin"))
                .isEqualTo(DocumentFormat.EPUB);
        assertThat(DocumentFormatDetector.detect("application/octet-stream", "book.epub"))
                .isEqualTo(DocumentFormat.EPUB);
    }

    private static Map<String, byte[]> baseEntries(String packageBody) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", """
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.getBytes(UTF_8));
        entries.put("OPS/package.opf", """
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata/>
                  %s
                </package>
                """.formatted(packageBody).getBytes(UTF_8));
        return entries;
    }

    private static byte[] xhtml(String title, String paragraph, String item) {
        String list = item.isBlank() ? "" : "<ul><li>" + item + "</li></ul>";
        return """
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <body><main><h1>%s</h1><p>%s</p>%s</main></body>
                </html>
                """.formatted(title, paragraph, list).getBytes(UTF_8);
    }

    private static byte[] epub(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
