package studio.one.platform.thumbnail;

public record ThumbnailOptions(int size, String format, long maxSourcePixels, long maxSourceBytes) {

    private static final long DEFAULT_MAX_SOURCE_BYTES = 50L * 1024L * 1024L;

    public ThumbnailOptions {
        if (size <= 0) {
            throw new IllegalArgumentException("Thumbnail size must be positive");
        }
        if (maxSourcePixels <= 0) {
            throw new IllegalArgumentException("Thumbnail max source pixels must be positive");
        }
        if (maxSourceBytes <= 0) {
            throw new IllegalArgumentException("Thumbnail max source bytes must be positive");
        }
        format = ThumbnailFormats.normalize(format);
    }

    public ThumbnailOptions(int size, String format, long maxSourcePixels) {
        this(size, format, maxSourcePixels, DEFAULT_MAX_SOURCE_BYTES);
    }
}
