package studio.one.platform.textract.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedFile;

class FormatGoldenTest {

    private static final String FIELD = "|";
    private static final String ROW = "↵";

    @Test
    void pdfGoldenKeepsPageAndParagraphProvenance() throws Exception {
        ParsedFile result = new PdfFileParser().parseStructured(pdfBytes(), "application/pdf", "golden.pdf");

        GoldenSnapshot snapshot = snapshot(result);

        assertEquals(DocumentFormat.PDF.name(), snapshot.format());
        assertEquals(List.of("PAGE|1|page[1]|0", "PAGE|2|page[2]|2"), snapshot.pages());
        assertEquals(List.of(
                "PARAGRAPH|1|page[1]/paragraph[0]|1|First page body↵Second paragraph",
                "PARAGRAPH|2|page[2]/paragraph[0]|3|Second page body"), snapshot.blocks());
        assertTrue(snapshot.plainText().contains("First page body"));
    }

    @Test
    void docxGoldenCapturesHeaderFooterTableListAndFootnote() throws Exception {
        ParsedFile result = new DocxFileParser().parseStructured(
                docxBytes(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "golden.docx");

        GoldenSnapshot snapshot = snapshot(result);

        assertEquals(DocumentFormat.DOCX.name(), snapshot.format());
        assertTrue(snapshot.blocks().contains("HEADING||body/element[0]|0|문서 제목"));
        assertTrue(snapshot.blocks().contains("LIST_ITEM||body/element[2]|2|목록 항목"));
        assertTrue(snapshot.blocks().stream().anyMatch(block -> block.startsWith("TABLE||body/element[3]|3|")));
        assertTrue(snapshot.blocks().stream().anyMatch(block -> block.startsWith("HEADER||header[0]/element[0]|")));
        assertTrue(snapshot.blocks().stream().anyMatch(block -> block.startsWith("FOOTER||footer[0]/element[0]|")));
        assertTrue(snapshot.blocks().stream().anyMatch(block -> block.startsWith("FOOTNOTE||footnote[")));
        assertEquals(List.of("docx|body/element[3]|2x2|4"), snapshot.tables());
    }

    @Test
    void pptxGoldenCapturesTitleBodyFooterAndSlide() throws Exception {
        ParsedFile result = new PptxFileParser().parseStructured(
                pptxBytes(),
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "golden.pptx");

        GoldenSnapshot snapshot = snapshot(result);

        assertEquals(DocumentFormat.PPTX.name(), snapshot.format());
        assertEquals(List.of("PAGE||slide[1]|0"), snapshot.pages());
        assertEquals(List.of(
                "TITLE|1|slide[1]/shape[0]|0|슬라이드 제목",
                "PARAGRAPH|1|slide[1]/shape[1]|1|본문 내용",
                "FOOTER|1|slide[1]/shape[2]|2|푸터"), snapshot.blocks());
    }

    @Test
    void htmlGoldenRemovesBoilerplateAndCapturesSemanticObjects() {
        ParsedFile result = new HtmlFileParser().parseStructured(htmlBytes(), "text/html", "golden.html");

        GoldenSnapshot snapshot = snapshot(result);

        assertEquals(DocumentFormat.HTML.name(), snapshot.format());
        assertEquals(List.of(
                "TITLE||h1[0]|0|문서 제목",
                "PARAGRAPH||p[1]|1|본문 문단",
                "LIST_ITEM||li[2]|2|목록 항목",
                "TABLE||table[3]|3|| A | B |↵| 1 | 2 |",
                "IMAGE_CAPTION||img[4]|4|대표 이미지"), snapshot.blocks());
        assertEquals(List.of("html|table[3]|2x2|4"), snapshot.tables());
        assertEquals(List.of("|img[4]|hero.png"), snapshot.images());
        assertTrue(!snapshot.plainText().contains("메뉴 링크"));
    }

    @Test
    void hwpxGoldenCapturesTableImageAndPartialWarning() throws Exception {
        HwpHwpxFileParser parser = new HwpHwpxFileParser();

        ParsedFile parsed = parser.parseStructured(hwpxBytes(), "application/hwpx", "golden.hwpx");
        ParsedFile partial = parser.parseStructured(hwpxBytesWithMissingSection(), "application/hwpx", "missing.hwpx");

        GoldenSnapshot snapshot = snapshot(parsed);
        GoldenSnapshot partialSnapshot = snapshot(partial);

        assertEquals(DocumentFormat.HWPX.name(), snapshot.format());
        assertEquals(List.of("hwpx|section[0]/p[3]/tbl[1]|2x2|4"), snapshot.tables());
        assertEquals(List.of("image/png|section[0]/p[5]/pic[0]|Contents/BinData/image1.png"), snapshot.images());
        assertEquals(List.of("WARNING|HWPX_SECTION_MISSING|true|section0.xml"), partialSnapshot.warnings());
    }

    @Test
    void hwpGoldenCapturesBodyTextAndEncryptedWarning() throws Exception {
        HwpHwpxFileParser parser = new HwpHwpxFileParser();

        ParsedFile parsed = parser.parseStructured(hwpBytesWithFlags(0), "application/x-hwp", "golden.hwp");
        ParsedFile encrypted = parser.parseStructured(hwpBytesWithFlags(0x02), "application/x-hwp", "encrypted.hwp");

        assertEquals(List.of("PARAGRAPH||section[0]/paragraph[0]|0|한글 본문"), snapshot(parsed).blocks());
        assertEquals(List.of("ERROR|HWP_ENCRYPTED|false|FileHeader"), snapshot(encrypted).warnings());
    }

    @Test
    void ocrGoldenCapturesKoreanLineBlocksWithoutEngineDependency() {
        List<String> blocks = new ImageFileParser("/tmp", "kor+eng").ocrLineBlocks("""
                첫 줄
                둘째 줄
                """).stream()
                .map(block -> block.blockType() + FIELD + block.sourceRef() + FIELD + block.order() + FIELD + block.text())
                .toList();

        assertEquals(List.of(
                "OCR_TEXT|image/ocr/line[0]|0|첫 줄",
                "OCR_TEXT|image/ocr/line[1]|1|둘째 줄"), blocks);
    }

    private GoldenSnapshot snapshot(ParsedFile file) {
        return new GoldenSnapshot(
                file.format().name(),
                file.plainText(),
                file.blocks().stream()
                        .map(block -> block.blockType()
                                + FIELD + blockLocation(block.page(), block.slide())
                                + FIELD + block.sourceRef()
                                + FIELD + value(block.order())
                                + FIELD + normalize(block.text()))
                        .toList(),
                file.pages().stream()
                        .map(page -> page.blockType()
                                + FIELD + value(page.page())
                                + FIELD + page.sourceRef()
                                + FIELD + value(page.order()))
                        .toList(),
                file.tables().stream()
                        .map(table -> table.format()
                                + FIELD + table.sourceRef()
                                + FIELD + table.rowCount() + "x" + table.cells().stream()
                                        .mapToInt(cell -> cell.col() + cell.colSpan())
                                        .max()
                                        .orElse(0)
                                + FIELD + table.cellCount())
                        .toList(),
                file.images().stream()
                        .map(image -> image.mimeType()
                                + FIELD + image.sourceRef()
                                + FIELD + imageReference(image.filename(), image.binDataRef()))
                        .toList(),
                file.warnings().stream()
                        .map(warning -> warning.severity()
                                + FIELD + warning.canonicalCode()
                                + FIELD + warning.partialParse()
                                + FIELD + warning.sourceRef())
                        .toList());
    }

    private String normalize(String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", ROW)
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String blockLocation(Integer page, Integer slide) {
        return slide != null ? slide.toString() : value(page);
    }

    private String imageReference(String filename, String binDataRef) {
        return binDataRef == null || binDataRef.isBlank() ? value(filename) : binDataRef;
    }

    private byte[] pdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Golden output intentionally omits these repeated boundaries; this guards the PDF boundary heuristic.
            addPdfPage(document, "Common header", "First page body", "Second paragraph", "Common footer");
            addPdfPage(document, "Common header", "Second page body", null, "Common footer");
            document.save(out);
            return out.toByteArray();
        }
    }

    private void addPdfPage(PDDocument document, String header, String first, String second, String footer)
            throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(50, 750);
            contentStream.showText(header);
            contentStream.newLineAtOffset(0, -40);
            contentStream.showText(first);
            if (second != null) {
                contentStream.newLineAtOffset(0, -40);
                contentStream.showText(second);
            }
            contentStream.newLineAtOffset(0, -600);
            contentStream.showText(footer);
            contentStream.endText();
        }
    }

    private byte[] docxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText("문서 제목");
            document.createParagraph().createRun().setText("본문 문단");
            XWPFParagraph list = document.createParagraph();
            list.setNumID(java.math.BigInteger.ONE);
            list.createRun().setText("목록 항목");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("A1");
            table.getRow(0).getCell(1).setText("B1");
            table.getRow(1).getCell(0).setText("A2");
            table.getRow(1).getCell(1).setText("B2");
            XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("문서 헤더");
            XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
            footer.createParagraph().createRun().setText("문서 푸터");
            XWPFFootnote footnote = document.createFootnote();
            footnote.createParagraph().createRun().setText("각주 본문");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] pptxBytes() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(640, 480));
            XSLFSlide slide = ppt.createSlide();
            addTextBox(slide, "슬라이드 제목", new Rectangle(20, 20, 500, 50));
            addTextBox(slide, "본문 내용", new Rectangle(20, 120, 500, 80));
            addTextBox(slide, "푸터", new Rectangle(20, 420, 500, 30));
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private void addTextBox(XSLFSlide slide, String text, Rectangle anchor) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setText(text);
        textBox.setAnchor(anchor);
    }

    private byte[] htmlBytes() {
        return """
                <html>
                  <body>
                    <nav>메뉴 링크</nav>
                    <main>
                      <h1>문서 제목</h1>
                      <p>본문 문단</p>
                      <ul><li>목록 항목</li></ul>
                      <table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>
                      <img src="hero.png" alt="대표 이미지">
                    </main>
                  </body>
                </html>
                """.getBytes(UTF_8);
    }

    private byte[] hwpxBytes() throws Exception {
        Map<String, byte[]> entries = Map.of(
                "Contents/content.hpf", """
                        <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
                          <opf:manifest>
                            <opf:item id="section0" href="section0.xml" media-type="application/xml"/>
                            <opf:item id="image1" href="BinData/image1.png" media-type="image/png"/>
                          </opf:manifest>
                          <opf:spine><opf:itemref idref="section0"/></opf:spine>
                        </opf:package>
                        """.getBytes(UTF_8),
                "Contents/section0.xml", """
                        <hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section"
                                xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
                          <hp:p><hp:run><hp:t>본문 문단</hp:t></hp:run></hp:p>
                          <hp:p>
                            <hp:tbl>
                              <hp:tr>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>A1</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>B1</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                              </hp:tr>
                              <hp:tr>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>A2</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>B2</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                              </hp:tr>
                            </hp:tbl>
                          </hp:p>
                          <hp:p><hp:pic><hp:img binaryItemIDRef="image1"/></hp:pic></hp:p>
                        </hs:sec>
                        """.getBytes(UTF_8),
                "Contents/BinData/image1.png", new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        return zip(entries);
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

    private byte[] hwpBytesWithFlags(int flags) throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.getRoot().createDocument("FileHeader", new ByteArrayInputStream(fileHeader(flags)));
            DirectoryEntry bodyText = fs.getRoot().createDirectory("BodyText");
            bodyText.createDocument("Section0", new ByteArrayInputStream(section("한글 본문")));
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    private byte[] fileHeader(int flags) {
        byte[] header = new byte[256];
        byte[] signature = "HWP Document File".getBytes(UTF_8);
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[35] = 5;
        header[36] = (byte) flags;
        header[37] = (byte) (flags >>> 8);
        header[38] = (byte) (flags >>> 16);
        header[39] = (byte) (flags >>> 24);
        return header;
    }

    private byte[] section(String text) throws Exception {
        byte[] paraHeaderData = new byte[12];
        byte[] paraTextData = paraText(text);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(record(66, 0, paraHeaderData));
        out.write(record(67, 1, paraTextData));
        return out.toByteArray();
    }

    private byte[] paraText(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(text.getBytes(UTF_16LE));
        out.write(new byte[] { 0x0D, 0x00 });
        return out.toByteArray();
    }

    private byte[] record(int tagId, int level, byte[] data) throws Exception {
        int header = tagId | (level << 10) | (data.length << 20);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {
                (byte) header,
                (byte) (header >>> 8),
                (byte) (header >>> 16),
                (byte) (header >>> 24)
        });
        out.write(data);
        return out.toByteArray();
    }

    private record GoldenSnapshot(
            String format,
            String plainText,
            List<String> blocks,
            List<String> pages,
            List<String> tables,
            List<String> images,
            List<String> warnings) {
    }
}
