package studio.one.platform.thumbnail;

public record ThumbnailOptions(int size, String format, long maxSourcePixels) {

    public ThumbnailOptions {
        if (size <= 0) {
            throw new IllegalArgumentException("Thumbnail size must be positive");
        }
        if (maxSourcePixels <= 0) {
            throw new IllegalArgumentException("Thumbnail max source pixels must be positive");
        }
        format = ThumbnailFormats.normalize(format);
    }
}
