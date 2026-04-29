package studio.one.platform.thumbnail.renderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageReader;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import studio.one.platform.thumbnail.ThumbnailFormats;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailImages;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderLimits;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;

public class ImageThumbnailRenderer implements ThumbnailRenderer {

    @Override
    public boolean supports(ThumbnailSource source) {
        String contentType = source.contentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/") && !contentType.equals("image/svg+xml")) {
            return true;
        }
        String filename = source.filename().toLowerCase(Locale.ROOT);
        return filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".gif")
                || filename.endsWith(".bmp");
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                new ByteArrayInputStream(source.bytes()))) {
            if (imageInput == null) {
                throw new ThumbnailGenerationException("Unsupported image thumbnail source");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new ThumbnailGenerationException("Unsupported image thumbnail source");
            }
            ImageReader reader = readers.next();
            BufferedImage original;
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                ThumbnailRenderLimits.requirePixelsWithinLimit(
                        width, height, options.maxSourcePixels(), source.filename());
                original = reader.read(0);
            } finally {
                reader.dispose();
            }
            BufferedImage scaled = ThumbnailImages.scale(original, options.size());
            byte[] bytes = ThumbnailImages.write(scaled, options.format());
            return new ThumbnailResult(bytes, ThumbnailFormats.contentType(options.format()), options.format());
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to read image thumbnail source", ex);
        }
    }
}
