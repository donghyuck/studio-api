package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ParsedFile;

class PdfFileParserTest {

    private final PdfFileParser parser = new PdfFileParser();
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Test
    void cleanPdfTextCompactsFragmentedShortLines() {
        String raw = """
                증
                주
                업
                을
                왼쪽
                유
                문
                로맨스로
                고는
                오른쪽과
                그
                에 있는 캐릭터는 삶을 살면서 서로의 감정을 나누는
                """;

        String result = parser.cleanPdfText(raw);

        assertEquals("증주업을왼쪽유문로맨스로고는오른쪽과그에 있는 캐릭터는 삶을 살면서 서로의 감정을 나누는", result);
    }

    @Test
    void cleanPdfTextKeepsNormalLineBreaks() {
        String raw = """
                첫 번째 문단입니다.
                다음 줄도 문단 구조입니다.

                두 번째 문단입니다.
                정상적인 줄바꿈은 유지합니다.
                """;

        String result = parser.cleanPdfText(raw);

        assertEquals("""
                첫 번째 문단입니다.
                다음 줄도 문단 구조입니다.

                두 번째 문단입니다.
                정상적인 줄바꿈은 유지합니다.""", result);
    }

    @Test
    void cleanPdfPagesRemovesRepeatedBoundaryLines() {
        List<String> result = parser.cleanPdfPages(List.of(
                """
                공통 헤더
                첫 페이지 본문
                공통 푸터
                """,
                """
                공통 헤더
                둘째 페이지 본문
                공통 푸터
                """));

        assertEquals(List.of("첫 페이지 본문", "둘째 페이지 본문"), result);
    }

    @Test
    void cleanPdfPagesKeepsBoundaryLinesBelowRepeatedRatio() {
        List<String> result = parser.cleanPdfPages(List.of(
                """
                우연히 같은 첫 줄
                첫 페이지 본문
                끝1
                """,
                """
                우연히 같은 첫 줄
                둘째 페이지 본문
                끝2
                """,
                """
                다른 첫 줄
                셋째 페이지 본문
                끝3
                """,
                """
                또 다른 첫 줄
                넷째 페이지 본문
                끝4
                """,
                """
                마지막 첫 줄
                다섯째 페이지 본문
                끝5
                """));

        assertTrue(result.get(0).contains("우연히 같은 첫 줄"));
        assertTrue(result.get(1).contains("우연히 같은 첫 줄"));
    }

    @Test
    void parseStructuredReturnsParagraphBlocksAndPageProvenance() throws Exception {
        byte[] bytes = pdfWithTwoPages();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "sample.pdf");

        assertEquals(DocumentFormat.PDF, result.format());
        assertTrue(result.plainText().contains("First page body"));
        assertEquals(2, result.pages().size());
        assertTrue(result.blocks().stream().allMatch(block -> block.blockType() == BlockType.PARAGRAPH));
        assertEquals(0, result.pages().get(0).order());
        assertEquals(1, result.blocks().get(0).page());
        assertEquals(1, result.blocks().get(0).order());
        assertEquals("page[1]/paragraph[0]", result.blocks().get(0).sourceRef());
    }

    @Test
    void parseStructuredExtractsImageXObjectWithPageSourceRef() throws Exception {
        byte[] bytes = pdfWithImage();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "image.pdf");

        assertEquals(1, result.images().size());
        ExtractedImage image = result.images().get(0);
        assertEquals("image/png", image.mimeType());
        assertEquals(1, image.width());
        assertEquals(1, image.height());
        assertEquals("page[1]/image[0]", image.sourceRef());
        assertEquals("page[1]/image[0]", image.binDataRef());
    }

    @Test
    void parseStructuredIgnoresUnusedImageXObjectResources() throws Exception {
        byte[] bytes = pdfWithUnusedImageResource();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "unused-image.pdf");

        assertEquals(0, result.images().size());
    }

    @Test
    void parseStructuredReconstructsSimpleAlignedTableCandidate() throws Exception {
        byte[] bytes = pdfWithTableText();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "table.pdf");

        assertEquals(1, result.tables().size());
        ExtractedTable table = result.tables().get(0);
        assertEquals("pdf", table.format());
        assertEquals("page[1]/table[0]", table.sourceRef());
        assertEquals(4, table.cellCount());
        assertEquals("""
                | Name | Score |
                | --- | --- |
                | Alice | 90 |""", table.markdown());
        assertEquals("Name", table.cells().get(0).text());
        assertTrue(table.cells().get(0).header());
        int tableBlockIndex = blockIndex(result, BlockType.TABLE);
        assertTrue(tableBlockIndex > 0);
        assertEquals(2, result.blocks().get(tableBlockIndex).order());
    }

    @Test
    void parseStructuredWarnsForAmbiguousTableCandidateWithoutFalseTable() throws Exception {
        byte[] bytes = pdfWithUnevenColumnTableCandidate();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "ambiguous-table.pdf");

        assertEquals(0, result.tables().size());
        assertEquals(1, result.warnings().size());
        assertEquals("TABLE_RECONSTRUCTION_PARTIAL", result.warnings().get(0).canonicalCode());
        assertTrue(result.warnings().get(0).partialParse());
    }

    @Test
    void parseStructuredDoesNotCreateTableForNormalParagraphSpacing() throws Exception {
        byte[] bytes = pdfWithNormalParagraph();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "normal.pdf");

        assertEquals(0, result.tables().size());
        assertEquals(0, result.warnings().size());
        assertTrue(result.plainText().contains("This is a normal paragraph"));
    }

    @Test
    void parseStructuredIgnoresSingleAlignedLineWithoutTableWarning() throws Exception {
        byte[] bytes = pdfWithSingleAlignedLine();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "single-aligned.pdf");

        assertEquals(0, result.tables().size());
        assertEquals(0, result.warnings().size());
        assertTrue(result.plainText().contains("Total"));
    }

    private byte[] pdfWithTwoPages() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addPage(document, "Common header", "First page body", "Common footer");
            addPage(document, "Common header", "Second page body", "Common footer");
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithImage() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, PNG_BYTES, "inline.png");
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(image, 50, 700, 20, 20);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithUnusedImageResource() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDResources resources = new PDResources();
            resources.add(PDImageXObject.createFromByteArray(document, PNG_BYTES, "unused.png"));
            page.setResources(resources);
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithTableText() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Name    Score");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Alice   90");
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithUnevenColumnTableCandidate() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Name    Score");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Alice   90    Passed");
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithNormalParagraph() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addPage(document, "This is a normal paragraph", "with regular spacing", "and no table");
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] pdfWithSingleAlignedLine() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Total    100");
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private void addPage(PDDocument document, String header, String body, String footer) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(50, 750);
            contentStream.showText(header);
            contentStream.newLineAtOffset(0, -40);
            contentStream.showText(body);
            contentStream.newLineAtOffset(0, -640);
            contentStream.showText(footer);
            contentStream.endText();
        }
    }

    private int blockIndex(ParsedFile result, BlockType blockType) {
        for (int index = 0; index < result.blocks().size(); index++) {
            if (result.blocks().get(index).blockType() == blockType) {
                return index;
            }
        }
        return -1;
    }
}
