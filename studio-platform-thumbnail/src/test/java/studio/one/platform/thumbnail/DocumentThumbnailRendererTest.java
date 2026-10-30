package studio.one.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import studio.one.platform.thumbnail.renderer.PptxThumbnailRenderer;

class DocumentThumbnailRendererTest {

    @Test
    void pptxRendererGeneratesThumbnailFromSlide() throws Exception {
        ThumbnailGenerationService service = service(List.of(new PptxThumbnailRenderer(0)));

        ThumbnailResult result = service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "sample.pptx",
                        pptxFixture(new Dimension(960, 540))),
                96,
                "png").orElseThrow();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getWidth()).isLessThanOrEqualTo(96);
        assertThat(image.getHeight()).isLessThanOrEqualTo(96);
    }

    @Test
    void pptxRendererRejectsOversizedSlidePixels() throws Exception {
        ThumbnailGenerationService service = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new PptxThumbnailRenderer(0))),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 1_000));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "large.pptx",
                        pptxFixture(new Dimension(4000, 4000))),
                96,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("exceed max pixels");
    }

    @Test
    void pptxRendererRejectsOversizedExtractedPackageEntryBeforePoiParsing() {
        ThumbnailGenerationService service = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new PptxThumbnailRenderer(0))),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024, 25_000_000));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "large.pptx",
                        zipWithEntry("ppt/slides/slide1.xml", new byte[2048])),
                96,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("entry exceeds max extracted bytes");
    }

    @Test
    void pptxRendererRejectsOversizedExtractedDirectoryEntryBeforePoiParsing() {
        ThumbnailGenerationService service = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new PptxThumbnailRenderer(0))),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024, 25_000_000));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "large.pptx",
                        zipWithEntry("ppt/media/", new byte[2048])),
                96,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("entry exceeds max extracted bytes");
    }

    @Test
    void pptxRendererRejectsDirectoryAndFileEntriesThatExceedTotalExtractedBytes() {
        ThumbnailGenerationService service = new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new PptxThumbnailRenderer(0))),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024, 25_000_000));

        assertThatThrownBy(() -> service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "large.pptx",
                        zipWithEntries(
                                List.of(
                                        new ZipFixtureEntry("ppt/media/", new byte[600]),
                                        new ZipFixtureEntry("ppt/slides/slide1.xml", new byte[600])))),
                96,
                "png"))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("exceeds max extracted bytes");
    }

    @Test
    void pptxRendererGeneratesThumbnailWhenPackageContainsEmptyDirectory() throws Exception {
        ThumbnailGenerationService service = service(List.of(new PptxThumbnailRenderer(0)));

        ThumbnailResult result = service.generate(
                new ThumbnailSource(
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "sample.pptx",
                        pptxFixtureWithEmptyDirectory(new Dimension(960, 540))),
                96,
                "png").orElseThrow();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(image.getWidth()).isLessThanOrEqualTo(96);
        assertThat(image.getHeight()).isLessThanOrEqualTo(96);
    }

    @Test
    void pptxRendererDoesNotSupportNonPptxSource() {
        PptxThumbnailRenderer renderer = new PptxThumbnailRenderer(0);

        assertThat(renderer.supports(new ThumbnailSource("application/pdf", "sample.pdf", new byte[] {1})))
                .isFalse();
    }

    private static ThumbnailGenerationService service(List<ThumbnailRenderer> renderers) {
        return new ThumbnailGenerationService(
                new ThumbnailRendererFactory(renderers),
                new ThumbnailGenerationOptions(128, "png", 16, 512, 1024 * 1024, 25_000_000));
    }

    private static byte[] pptxFixture(Dimension pageSize) throws Exception {
        try (XMLSlideShow presentation = new XMLSlideShow();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            presentation.setPageSize(pageSize);
            XSLFSlide slide = presentation.createSlide();
            XSLFTextBox title = slide.createTextBox();
            title.setAnchor(new Rectangle(60, 60, Math.max(120, pageSize.width - 120), 120));
            title.setFillColor(Color.WHITE);
            title.setLineColor(Color.WHITE);
            title.setText("Thumbnail preview");
            presentation.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pptxFixtureWithEmptyDirectory(Dimension pageSize) throws Exception {
        byte[] pptx = pptxFixture(pageSize);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(pptx));
                ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                int read;
                while ((read = input.read(buffer)) != -1) {
                    zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("ppt/media/"));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] zipWithEntry(String name, byte[] data) throws Exception {
        return zipWithEntries(List.of(new ZipFixtureEntry(name, data)));
    }

    private static byte[] zipWithEntries(List<ZipFixtureEntry> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ZipFixtureEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.data());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private record ZipFixtureEntry(String name, byte[] data) {
    }
}
