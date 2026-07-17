package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import studio.one.platform.markdown.application.MarkdownPagePreviewBounds;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;

class PdfBoxMarkdownPagePreviewAdapterTest {

    @Test
    void rendersWholePageAndCoordinateCropAsPng() throws Exception {
        byte[] pdf = pdf();
        PdfBoxMarkdownPagePreviewAdapter adapter = new PdfBoxMarkdownPagePreviewAdapter();
        MarkdownSourcePort.MarkdownSource source = new MarkdownSourcePort.MarkdownSource(
                1L, "sample.pdf", "application/pdf", "ATTACHMENT", "1", pdf);

        byte[] page = adapter.renderPng(source, 1, null);
        byte[] crop = adapter.renderPng(source, 1, new MarkdownPagePreviewBounds(10, 10, 100, 100));

        assertThat(page).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(crop)).getWidth())
                .isLessThan(ImageIO.read(new java.io.ByteArrayInputStream(page)).getWidth());
    }

    private byte[] pdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
