package studio.one.platform.thumbnail;

public record ThumbnailGenerationOptions(
        int defaultSize,
        String defaultFormat,
        int minSize,
        int maxSize,
        long maxSourceBytes,
        long maxSourcePixels) {

    public ThumbnailGenerationOptions {
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
        defaultSize = Math.max(minSize, Math.min(maxSize, defaultSize));
        defaultFormat = ThumbnailFormats.normalizeOrDefault(defaultFormat, ThumbnailFormats.DEFAULT_FORMAT);
    }
}
