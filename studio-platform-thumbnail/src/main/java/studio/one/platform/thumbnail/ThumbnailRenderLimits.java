package studio.one.platform.thumbnail;

public final class ThumbnailRenderLimits {

    private ThumbnailRenderLimits() {
    }

    public static void requirePixelsWithinLimit(
            long width,
            long height,
            long maxPixels,
            String sourceDescription) {
        if (width <= 0 || height <= 0) {
            throw new ThumbnailGenerationException("Invalid thumbnail source dimensions: "
                    + sourceDescription + " " + width + "x" + height);
        }
        if (width > Long.MAX_VALUE / height) {
            throw new ThumbnailGenerationException("Thumbnail source dimensions overflow: "
                    + sourceDescription + " " + width + "x" + height);
        }
        long pixels = width * height;
        if (pixels > maxPixels) {
            throw new ThumbnailGenerationException("Thumbnail source dimensions exceed max pixels "
                    + maxPixels + ": " + sourceDescription + " " + width + "x" + height);
        }
    }
}
