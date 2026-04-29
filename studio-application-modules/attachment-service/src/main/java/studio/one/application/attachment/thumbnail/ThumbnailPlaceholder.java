package studio.one.application.attachment.thumbnail;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

final class ThumbnailPlaceholder {

    private ThumbnailPlaceholder() {
    }

    static ThumbnailData pending(int size) {
        int resolvedSize = Math.max(16, size);
        BufferedImage image = new BufferedImage(resolvedSize, resolvedSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(241, 245, 249));
            graphics.fillRect(0, 0, resolvedSize, resolvedSize);
            graphics.setColor(new Color(148, 163, 184));
            graphics.drawRect(0, 0, resolvedSize - 1, resolvedSize - 1);

            graphics.setColor(new Color(71, 85, 105));
            int fontSize = Math.max(9, Math.min(14, resolvedSize / 9));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            drawCentered(graphics, "Pending", resolvedSize, resolvedSize);
        } finally {
            graphics.dispose();
        }
        return new ThumbnailData(writePng(image), "image/png", "pending");
    }

    private static void drawCentered(Graphics2D graphics, String text, int width, int height) {
        FontMetrics metrics = graphics.getFontMetrics();
        int x = Math.max(0, (width - metrics.stringWidth(text)) / 2);
        int y = Math.max(metrics.getAscent(), (height - metrics.getHeight()) / 2 + metrics.getAscent());
        graphics.drawString(text, x, y);
    }

    private static byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write thumbnail placeholder", ex);
        }
    }
}
