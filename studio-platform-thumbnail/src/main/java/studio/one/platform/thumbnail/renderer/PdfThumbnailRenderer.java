package studio.one.platform.thumbnail.renderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import studio.one.platform.thumbnail.ThumbnailFormats;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailImages;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderLimits;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;

public class PdfThumbnailRenderer implements ThumbnailRenderer {

    private static final float DEFAULT_DPI = 144f;

    private final int page;

    public PdfThumbnailRenderer(int page) {
        this.page = Math.max(0, page);
    }

    @Override
    public boolean supports(ThumbnailSource source) {
        String contentType = source.contentType().toLowerCase(Locale.ROOT);
        if (contentType.equals("application/pdf")) {
            return true;
        }
        return source.filename().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        try (PDDocument document = Loader.loadPDF(source.bytes())) {
            if (document.getNumberOfPages() == 0) {
                throw new ThumbnailGenerationException("PDF thumbnail source has no pages");
            }
            int pageIndex = Math.min(page, document.getNumberOfPages() - 1);
            validatePagePixelSize(document.getPage(pageIndex), options, source.filename());
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, DEFAULT_DPI, ImageType.RGB);
            BufferedImage scaled = ThumbnailImages.scale(pageImage, options.size());
            byte[] bytes = ThumbnailImages.write(scaled, options.format());
            return new ThumbnailResult(bytes, ThumbnailFormats.contentType(options.format()), options.format());
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to render PDF thumbnail source", ex);
        }
    }

    private void validatePagePixelSize(PDPage page, ThumbnailOptions options, String filename) {
        PDRectangle box = page.getCropBox();
        long width = Math.max(1L, Math.round(Math.ceil(box.getWidth() * DEFAULT_DPI / 72d)));
        long height = Math.max(1L, Math.round(Math.ceil(box.getHeight() * DEFAULT_DPI / 72d)));
        ThumbnailRenderLimits.requirePixelsWithinLimit(width, height, options.maxSourcePixels(), filename);
    }
}
