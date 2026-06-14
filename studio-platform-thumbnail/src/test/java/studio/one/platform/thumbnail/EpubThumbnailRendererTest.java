package studio.one.platform.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import studio.one.platform.thumbnail.renderer.BatikEpubSvgRasterizer;
import studio.one.platform.thumbnail.renderer.EpubThumbnailRenderer;

class EpubThumbnailRendererTest {

    private static final ThumbnailOptions OPTIONS =
            new ThumbnailOptions(96, "png", 25_000_000, 50L * 1024 * 1024);

    @Test
    void extractsEpub3CoverImageFromNestedOpfPath() throws Exception {
        Map<String, byte[]> entries = baseEntries("OPS/package.opf", opf("""
                <item id="cover" href="images/cover.png" media-type="image/png"
                      properties="nav cover-image"/>
                """, ""));
        entries.put("OPS/images/cover.png", image(420, 620, Color.RED));

        BufferedImage thumbnail = render(epub(entries), null);

        assertThat(center(thumbnail)).isEqualTo(Color.RED.getRGB());
        assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(96);
        assertThat(thumbnail.getHeight()).isLessThanOrEqualTo(96);
    }

    @Test
    void extractsEpub2CoverReferencedByMetadata() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="legacy-cover" href="cover.jpg" media-type="image/jpeg"/>
                """, "<meta name=\"cover\" content=\"legacy-cover\"/>"));
        entries.put("cover.jpg", image(400, 600, Color.GREEN));

        assertThat(center(render(epub(entries), null))).isEqualTo(Color.GREEN.getRGB());
    }

    @Test
    void resolvesParentRelativeManifestHrefWithinPackage() throws Exception {
        Map<String, byte[]> entries = baseEntries("OPS/text/package.opf", opf("""
                <item id="cover" href="../images/cover.png" media-type="image/png"
                      properties="cover-image"/>
                """, ""));
        entries.put("OPS/images/cover.png", image(400, 600, Color.ORANGE));

        assertThat(center(render(epub(entries), null))).isEqualTo(Color.ORANGE.getRGB());
    }

    @Test
    void selectsLargestRasterManifestImageWhenCoverMetadataIsAbsent() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="small" href="small.png" media-type="image/png"/>
                <item id="large" href="large.png" media-type="image/png"/>
                <item id="below-min" href="wide.png" media-type="image/png"/>
                """, ""));
        entries.put("small.png", image(320, 320, Color.BLUE));
        entries.put("large.png", image(500, 700, Color.MAGENTA));
        entries.put("wide.png", image(900, 200, Color.YELLOW));

        assertThat(center(render(epub(entries), null))).isEqualTo(Color.MAGENTA.getRGB());
    }

    @Test
    void usesGeneratedIconWhenCoverIsMissingOrBroken() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="cover" href="broken.png" media-type="image/png"
                      properties="cover-image"/>
                """, ""));
        entries.put("broken.png", "not-an-image".getBytes(StandardCharsets.UTF_8));

        BufferedImage thumbnail = render(epub(entries), null);

        assertThat(thumbnail).isNotNull();
        assertThat(new Color(thumbnail.getRGB(2, 2))).isEqualTo(new Color(55, 86, 149));
    }

    @Test
    void rasterizesSvgCoverOnlyWhenBatikAdapterIsProvided() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="cover" href="cover.svg" media-type="image/svg+xml"
                      properties="cover-image"/>
                """, ""));
        entries.put("cover.svg", """
                <svg xmlns="http://www.w3.org/2000/svg" width="400" height="600">
                  <rect width="400" height="600" fill="#ff0000"/>
                </svg>
                """.getBytes(StandardCharsets.UTF_8));

        BatikEpubSvgRasterizer rasterizer = new BatikEpubSvgRasterizer();
        assertThat(center(rasterizer.rasterize(
                entries.get("cover.svg"), 96, 25_000_000, "cover.svg")))
                .isEqualTo(Color.RED.getRGB());
        assertThat(center(render(epub(entries), rasterizer)))
                .isEqualTo(Color.RED.getRGB());
        assertThat(new Color(render(epub(entries), null).getRGB(2, 2)))
                .isEqualTo(new Color(55, 86, 149));
    }

    @Test
    void rejectsZipEntryPathTraversal() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../outside.xml", "bad".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> renderer(null).render(source(epub(entries)), OPTIONS))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void rejectsOpfHrefThatEscapesPackageRoot() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="cover" href="../cover.png" media-type="image/png"
                      properties="cover-image"/>
                """, ""));

        assertThatThrownBy(() -> renderer(null).render(source(epub(entries)), OPTIONS))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void rejectsDoctypeAndExternalEntity() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", """
                <!DOCTYPE container [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="&xxe;"/></rootfiles>
                </container>
                """.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> renderer(null).render(source(epub(entries)), OPTIONS))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("Failed to parse EPUB XML");
    }

    @Test
    void rejectsOversizedExtractedEntry() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", new byte[2048]);
        ThumbnailOptions tightOptions = new ThumbnailOptions(96, "png", 25_000_000, 1024);

        assertThatThrownBy(() -> renderer(null).render(source(epub(entries)), tightOptions))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("entry exceeds max extracted bytes");
    }

    @Test
    void rejectsExcessiveZipEntryCount() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 2049; i++) {
            entries.put("entry-" + i, new byte[] {1});
        }

        assertThatThrownBy(() -> renderer(null).render(source(epub(entries)), OPTIONS))
                .isInstanceOf(ThumbnailGenerationException.class)
                .hasMessageContaining("max entry count");
    }

    @Test
    void oversizedCoverPixelsFallBackToGeneratedIcon() throws Exception {
        Map<String, byte[]> entries = baseEntries("content.opf", opf("""
                <item id="cover" href="cover.png" media-type="image/png"
                      properties="cover-image"/>
                """, ""));
        entries.put("cover.png", image(100, 100, Color.RED));
        ThumbnailOptions tightPixels = new ThumbnailOptions(96, "png", 1_000, 1024 * 1024);

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(
                renderer(null).render(source(epub(entries)), tightPixels).bytes()));

        assertThat(new Color(thumbnail.getRGB(2, 2))).isEqualTo(new Color(55, 86, 149));
    }

    @Test
    void supportsMimeTypeAndExtension() {
        EpubThumbnailRenderer renderer = renderer(null);

        assertThat(renderer.supports(new ThumbnailSource("application/epub+zip", "book.bin", new byte[] {1})))
                .isTrue();
        assertThat(renderer.supports(new ThumbnailSource("application/octet-stream", "book.EPUB", new byte[] {1})))
                .isTrue();
        assertThat(renderer.supports(new ThumbnailSource("application/pdf", "book.pdf", new byte[] {1})))
                .isFalse();
    }

    private static EpubThumbnailRenderer renderer(studio.one.platform.thumbnail.renderer.EpubSvgRasterizer rasterizer) {
        return new EpubThumbnailRenderer(300, 300, rasterizer);
    }

    private static BufferedImage render(byte[] epub, studio.one.platform.thumbnail.renderer.EpubSvgRasterizer rasterizer)
            throws Exception {
        ThumbnailResult result = renderer(rasterizer).render(source(epub), OPTIONS);
        assertThat(result.contentType()).isEqualTo("image/png");
        return ImageIO.read(new ByteArrayInputStream(result.bytes()));
    }

    private static ThumbnailSource source(byte[] epub) {
        return new ThumbnailSource("application/epub+zip", "book.epub", epub);
    }

    private static int center(BufferedImage image) {
        return image.getRGB(image.getWidth() / 2, image.getHeight() / 2);
    }

    private static Map<String, byte[]> baseEntries(String opfPath, byte[] opf) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("META-INF/container.xml", """
                <?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="%s" media-type="application/oebps-package+xml"/></rootfiles>
                </container>
                """.formatted(opfPath).getBytes(StandardCharsets.UTF_8));
        entries.put(opfPath, opf);
        return entries;
    }

    private static byte[] opf(String manifest, String metadata) {
        return """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata>%s</metadata>
                  <manifest>%s</manifest>
                  <spine/>
                </package>
                """.formatted(metadata, manifest).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] image(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
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
