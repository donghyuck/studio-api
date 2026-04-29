package studio.one.platform.thumbnail.renderer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import studio.one.platform.thumbnail.ThumbnailFormats;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailImages;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderLimits;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;

public class PptxThumbnailRenderer implements ThumbnailRenderer {

    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_ENTRY_BYTES = 16L * 1024L * 1024L;

    private final int slide;

    public PptxThumbnailRenderer(int slide) {
        this.slide = Math.max(0, slide);
    }

    @Override
    public boolean supports(ThumbnailSource source) {
        String contentType = source.contentType().toLowerCase(Locale.ROOT);
        if (contentType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return true;
        }
        return source.filename().toLowerCase(Locale.ROOT).endsWith(".pptx");
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        validatePackageBounds(source.bytes(), options.maxSourceBytes(), source.filename());
        try (XMLSlideShow presentation = new XMLSlideShow(new ByteArrayInputStream(source.bytes()))) {
            if (presentation.getSlides().isEmpty()) {
                throw new ThumbnailGenerationException("PPTX thumbnail source has no slides");
            }
            int slideIndex = Math.min(slide, presentation.getSlides().size() - 1);
            Dimension pageSize = presentation.getPageSize();
            int width = Math.max(1, pageSize.width);
            int height = Math.max(1, pageSize.height);
            ThumbnailRenderLimits.requirePixelsWithinLimit(width, height, options.maxSourcePixels(), source.filename());

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setPaint(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                XSLFSlide selected = presentation.getSlides().get(slideIndex);
                selected.draw(graphics);
            } finally {
                graphics.dispose();
            }
            BufferedImage scaled = ThumbnailImages.scale(image, options.size());
            byte[] bytes = ThumbnailImages.write(scaled, options.format());
            return new ThumbnailResult(bytes, ThumbnailFormats.contentType(options.format()), options.format());
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to render PPTX thumbnail source", ex);
        } catch (RuntimeException ex) {
            if (ex instanceof ThumbnailGenerationException thumbnailEx) {
                throw thumbnailEx;
            }
            throw new ThumbnailGenerationException("Failed to render PPTX thumbnail source", ex);
        }
    }

    private void validatePackageBounds(byte[] bytes, long maxSourceBytes, String filename) {
        long maxEntryBytes = Math.min(MAX_ENTRY_BYTES, maxSourceBytes);
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                long entryBytes = readBounded(zip, maxEntryBytes, entry.getName());
                totalBytes += entryBytes;
                if (totalBytes > maxSourceBytes) {
                    throw new ThumbnailGenerationException("PPTX package exceeds max extracted bytes "
                            + maxSourceBytes + ": " + filename);
                }
            }
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to validate PPTX package bounds", ex);
        }
    }

    private long readBounded(InputStream in, long maxBytes, String entryName) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ThumbnailGenerationException("PPTX package entry exceeds max extracted bytes "
                        + maxBytes + ": " + entryName);
            }
        }
        return total;
    }
}
