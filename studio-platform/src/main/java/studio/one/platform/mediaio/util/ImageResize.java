package studio.one.platform.mediaio.util;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class ImageResize {

    private ImageResize() {}
    
    public enum Fit { COVER, CONTAIN, FILL, INSIDE, OUTSIDE }

    public static BufferedImage resize(BufferedImage src, int width, int height, Fit fit) {
        int W = width, H = height;
        BufferedImage canvas = new BufferedImage(W, H,
                src.getType() == 0 ? BufferedImage.TYPE_INT_RGB : src.getType());
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double sx = W / (double) src.getWidth();
            double sy = H / (double) src.getHeight();
            switch (fit) {
                case COVER -> {
                    double s = Math.max(sx, sy);
                    int w = (int) Math.round(src.getWidth() * s);
                    int h = (int) Math.round(src.getHeight() * s);
                    g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
                    g.drawImage(src, (W - w) / 2, (H - h) / 2, w, h, null);
                }
                case CONTAIN, INSIDE -> {
                    double s = Math.min(sx, sy);
                    int w = (int) Math.round(src.getWidth() * s);
                    int h = (int) Math.round(src.getHeight() * s);
                    g.setColor(Color.WHITE); g.fillRect(0, 0, W, H);
                    g.drawImage(src, (W - w) / 2, (H - h) / 2, w, h, null);
                }
                case FILL, OUTSIDE -> g.drawImage(src, 0, 0, W, H, null);
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }
}
