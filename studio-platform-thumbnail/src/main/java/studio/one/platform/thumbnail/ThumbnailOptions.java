package studio.one.platform.thumbnail;

public class ThumbnailOptions {

    private static final long DEFAULT_MAX_SOURCE_BYTES = 50L * 1024L * 1024L;

    private final int size;
    private final String format;
    private final long maxSourcePixels;
    private final long maxSourceBytes;

    public ThumbnailOptions(int size, String format, long maxSourcePixels, long maxSourceBytes) {
        if (size <= 0) {
            throw new IllegalArgumentException("Thumbnail size must be positive");
        }
        if (maxSourcePixels <= 0) {
            throw new IllegalArgumentException("Thumbnail max source pixels must be positive");
        }
        if (maxSourceBytes <= 0) {
            throw new IllegalArgumentException("Thumbnail max source bytes must be positive");
        }
        this.size = size;
        this.format = ThumbnailFormats.normalize(format);
        this.maxSourcePixels = maxSourcePixels;
        this.maxSourceBytes = maxSourceBytes;
    }

    public ThumbnailOptions(int size, String format, long maxSourcePixels) {
        this(size, format, maxSourcePixels, DEFAULT_MAX_SOURCE_BYTES);
    }

    public int size() { return size; }

    public String format() { return format; }

    public long maxSourcePixels() { return maxSourcePixels; }

    public long maxSourceBytes() { return maxSourceBytes; }
}
