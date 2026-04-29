package studio.one.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;
import studio.one.platform.thumbnail.renderer.PdfThumbnailRenderer;

class ThumbnailGenerationServiceTest {

    @Test
    void imageRendererResizesImageInput() throws Exception {
        ThumbnailGenerationService service = service(List.of(new ImageThumbnailRenderer()),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));

        ThumbnailResult result = service.generate(
                new ThumbnailSource("image/png", "sample.png", imageBytes(400, 200)),
                64,
                "png").orElseThrow();

        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getWidth()).isLessThanOrEqualTo(64);
        assertThat(image.getHeight()).isLessThanOrEqualTo(64);
    }

    @Test
    void generationServiceClampsRequestedSizeAndFallsBackToDefaultFormat() {
        ThumbnailGenerationService service = service(List.of(new ImageThumbnailRenderer()),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));

        ThumbnailOptions small = service.resolveOptions(1, "jpeg");
        ThumbnailOptions large = service.resolveOptions(2000, null);

        assertThat(small.size()).isEqualTo(16);
        assertThat(small.format()).isEqualTo("png");
        assertThat(large.size()).isEqualTo(512);
        assertThat(large.format()).isEqualTo("png");
    }

    @Test
    void unsupportedInputReturnsEmptyResult() {
        ThumbnailGenerationService service = service(List.of(new ImageThumbnailRenderer()),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));

        assertThat(service.generate(new ThumbnailSource("text/plain", "sample.txt", "text".getBytes()), 128, "png"))
                .isEmpty();
    }

    @Test
    void maxSourceSizeRejectsOversizedInput() {
        ThumbnailGenerationService service = service(List.of(new ImageThumbnailRenderer()),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 4, 25_000_000));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource("image/png", "sample.png", new byte[] { 1, 2, 3, 4, 5 }),
                128,
                "png"))
                .isInstanceOf(ThumbnailSourceTooLargeException.class)
                .hasMessageContaining("4 bytes");
    }

    @Test
    void pdfRendererGeneratesThumbnailFromFirstPage() throws Exception {
        ThumbnailGenerationService service = service(List.of(new PdfThumbnailRenderer(0)),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));

        ThumbnailResult result = service.generate(
                new ThumbnailSource("application/pdf", "sample.pdf", pdfBytes()),
                96,
                "png").orElseThrow();

        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getWidth()).isLessThanOrEqualTo(96);
        assertThat(image.getHeight()).isLessThanOrEqualTo(96);
    }

    @Test
    void factoryUsesRendererOrder() {
        ThumbnailRenderer first = new TestRenderer(false);
        ThumbnailRenderer second = new TestRenderer(true);
        ThumbnailRendererFactory factory = new ThumbnailRendererFactory(List.of(first, second));

        assertThat(factory.findRenderer(new ThumbnailSource("application/test", "sample.bin", new byte[] { 1 })))
                .containsSame(second);
    }

    @Test
    void imageRendererRejectsDecodedSourcePixelsAboveLimit() throws Exception {
        ThumbnailGenerationService service = service(List.of(new ImageThumbnailRenderer()),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 1));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource("image/png", "sample.png", imageBytes(2, 2)),
                64,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("max pixels");
    }

    @Test
    void pdfRendererRejectsRenderedPagePixelsAboveLimit() throws Exception {
        ThumbnailGenerationService service = service(List.of(new PdfThumbnailRenderer(0)),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 1));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource("application/pdf", "sample.pdf", pdfBytes()),
                64,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("max pixels");
    }

    private static ThumbnailGenerationService service(
            List<ThumbnailRenderer> renderers,
            ThumbnailGenerationOptions options) {
        return new ThumbnailGenerationService(new ThumbnailRendererFactory(renderers), options);
    }

    private static byte[] imageBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLUE);
            graphics.fillRect(10, 10, Math.max(1, width - 20), Math.max(1, height - 20));
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] pdfBytes() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private record TestRenderer(boolean supports) implements ThumbnailRenderer {
        @Override
        public boolean supports(ThumbnailSource source) {
            return supports;
        }

        @Override
        public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
            return new ThumbnailResult(new byte[] { 1 }, "image/png", "png");
        }
    }
}
