package studio.one.platform.textract.infrastructure.extractor.pdf.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;
import studio.one.platform.textract.domain.model.ParsedFile;

class PdfBoxExtractionEngineTest {

    private final PdfBoxExtractionEngine engine = new PdfBoxExtractionEngine();

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

        assertThat(engine.cleanPdfText(raw))
                .isEqualTo("증주업을왼쪽유문로맨스로고는오른쪽과그에 있는 캐릭터는 삶을 살면서 서로의 감정을 나누는");
    }

    @Test
    void extractKeepsPdfBoxEngineMetadataAndPlainText() throws Exception {
        ParsedFile result = engine.extract(new PdfExtractionRequest(
                pdfBytes(),
                "application/pdf",
                "sample.pdf",
                PdfExtractionOptions.defaults()));

        assertThat(result.plainText()).contains("Hello PDF");
        assertThat(result.metadata()).containsEntry(PdfExtractionEngineSelector.KEY_EXTRACTION_ENGINE, "pdfbox");
    }

    private byte[] pdfBytes() throws Exception {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Hello PDF");
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
