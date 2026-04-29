package studio.one.platform.thumbnail;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public final class ThumbnailImages {

    private ThumbnailImages() {
    }

    public static BufferedImage scale(BufferedImage original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return original;
        }
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }
        return scaled;
    }

    public static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new ThumbnailGenerationException("No ImageIO writer for thumbnail format: " + format);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to write thumbnail", ex);
        }
    }
}
