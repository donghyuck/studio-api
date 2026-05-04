package studio.one.platform.textract.extractor.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.pdf.PdfExtractionEngineSelector;
import studio.one.platform.textract.extractor.pdf.PdfExtractionMode;
import studio.one.platform.textract.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParsedFile;

class PdfFileParserBackwardCompatibilityTest {

    @Test
    void noArgParserStillReturnsPdfBoxStructuredResult() throws Exception {
        ParsedFile result = new PdfFileParser().parseStructured(pdfBytes(), "application/pdf", "sample.pdf");

        assertThat(result.plainText()).contains("First page body");
        assertThat(result.blocks()).extracting(block -> block.blockType())
                .containsOnly(BlockType.PARAGRAPH);
        assertThat(result.pages()).hasSize(1);
    }

    @Test
    void parserDoesNotSupportPdfWhenNoEngineIsAvailable() {
        PdfExtractionOptions options = new PdfExtractionOptions(
                PdfExtractionMode.AUTO,
                true,
                false,
                false,
                false,
                false,
                false,
                null,
                3,
                1024);
        PdfFileParser parser = new PdfFileParser(new PdfExtractionEngineSelector(List.of()), options);

        assertThat(parser.supports("application/pdf", "sample.pdf")).isFalse();
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
                contentStream.showText("First page body");
                contentStream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
