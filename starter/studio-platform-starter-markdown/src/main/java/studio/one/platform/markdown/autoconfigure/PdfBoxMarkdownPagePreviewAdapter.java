package studio.one.platform.markdown.autoconfigure;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import studio.one.platform.markdown.application.MarkdownPagePreviewBounds;
import studio.one.platform.markdown.application.MarkdownPagePreviewUnavailableException;
import studio.one.platform.markdown.application.port.MarkdownPagePreviewPort;
import studio.one.platform.markdown.application.port.MarkdownSourcePort;

class PdfBoxMarkdownPagePreviewAdapter implements MarkdownPagePreviewPort {

    private static final float DPI = 144f;
    private static final long MAX_PIXELS = 24_000_000L;

    @Override
    public byte[] renderPng(MarkdownSourcePort.MarkdownSource source, int page, MarkdownPagePreviewBounds bounds) {
        try (PDDocument document = Loader.loadPDF(source.content())) {
            if (page < 1 || page > document.getNumberOfPages()) {
                throw new MarkdownPagePreviewUnavailableException(
                        "PDF page is outside the document range: " + page);
            }
            int pageIndex = page - 1;
            PDRectangle cropBox = document.getPage(pageIndex).getCropBox();
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, DPI, ImageType.RGB);
            BufferedImage preview = bounds == null ? image : crop(image, cropBox, bounds);
            requirePixelLimit(preview);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(preview, "png", output)) {
                    throw new MarkdownPagePreviewUnavailableException("PNG image writer is not available");
                }
                return output.toByteArray();
            }
        } catch (IOException ex) {
            throw new MarkdownPagePreviewUnavailableException("Failed to render PDF page preview", ex);
        }
    }

    private BufferedImage crop(BufferedImage source, PDRectangle pageBox, MarkdownPagePreviewBounds bounds) {
        double scaleX = source.getWidth() / Math.max(1d, pageBox.getWidth());
        double scaleY = source.getHeight() / Math.max(1d, pageBox.getHeight());
        int x = clampFloor(bounds.x0() * scaleX, 0, source.getWidth() - 1);
        int y = clampFloor(bounds.y0() * scaleY, 0, source.getHeight() - 1);
        int right = clampCeil(bounds.x1() * scaleX, x + 1, source.getWidth());
        int bottom = clampCeil(bounds.y1() * scaleY, y + 1, source.getHeight());
        BufferedImage cropped = new BufferedImage(right - x, bottom - y, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, cropped.getWidth(), cropped.getHeight(),
                    x, y, right, bottom, null);
        } finally {
            graphics.dispose();
        }
        return cropped;
    }

    private int clampFloor(double value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.floor(value)));
    }

    private int clampCeil(double value, int min, int max) {
        return Math.max(min, Math.min(max, (int) Math.ceil(value)));
    }

    private void requirePixelLimit(BufferedImage image) {
        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels > MAX_PIXELS) {
            throw new MarkdownPagePreviewUnavailableException(
                    "PDF page preview exceeds the pixel limit: " + pixels);
        }
    }
}
