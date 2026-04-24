package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedFile;

class PdfFileParserTest {

    private final PdfFileParser parser = new PdfFileParser();

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
    void parseStructuredReturnsParagraphBlocksAndPageProvenance() throws Exception {
        byte[] bytes = pdfWithTwoPages();

        ParsedFile result = parser.parseStructured(bytes, "application/pdf", "sample.pdf");

        assertEquals(DocumentFormat.PDF, result.format());
        assertTrue(result.plainText().contains("First page body"));
        assertEquals(2, result.pages().size());
        assertTrue(result.blocks().stream().allMatch(block -> block.blockType() == BlockType.PARAGRAPH));
        assertEquals(1, result.blocks().get(0).page());
        assertEquals("page[1]/paragraph[0]", result.blocks().get(0).sourceRef());
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
}
