package studio.one.platform.thumbnail.renderer;

import java.awt.image.BufferedImage;

public interface EpubSvgRasterizer {

    BufferedImage rasterize(byte[] svgBytes, int targetSize, long maxSourcePixels, String sourceDescription);
}
