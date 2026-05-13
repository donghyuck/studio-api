package studio.one.platform.thumbnail;

public class ThumbnailGenerationOptions {

    private final int defaultSize;
    private final String defaultFormat;
    private final int minSize;
    private final int maxSize;
    private final long maxSourceBytes;
    private final long maxSourcePixels;

    public ThumbnailGenerationOptions(int defaultSize, String defaultFormat, int minSize, int maxSize,
            long maxSourceBytes, long maxSourcePixels) {
        if (minSize <= 0) {
            throw new IllegalArgumentException("studio.thumbnail.min-size must be positive");
        }
        if (maxSize < minSize) {
            throw new IllegalArgumentException("studio.thumbnail.max-size must be greater than or equal to min-size");
        }
        if (defaultSize <= 0) {
            throw new IllegalArgumentException("studio.thumbnail.default-size must be positive");
        }
        if (maxSourceBytes <= 0 || maxSourceBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("studio.thumbnail.max-source-size must be between 1B and "
                    + Integer.MAX_VALUE + "B");
        }
        if (maxSourcePixels <= 0) {
            throw new IllegalArgumentException("studio.thumbnail.max-source-pixels must be positive");
        }
        this.defaultSize = Math.max(minSize, Math.min(maxSize, defaultSize));
        this.defaultFormat = ThumbnailFormats.normalizeOrDefault(defaultFormat, ThumbnailFormats.DEFAULT_FORMAT);
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.maxSourceBytes = maxSourceBytes;
        this.maxSourcePixels = maxSourcePixels;
    }

    public int defaultSize() { return defaultSize; }

    public String defaultFormat() { return defaultFormat; }

    public int minSize() { return minSize; }

    public int maxSize() { return maxSize; }

    public long maxSourceBytes() { return maxSourceBytes; }

    public long maxSourcePixels() { return maxSourcePixels; }
}
