package studio.one.platform.thumbnail;

public class ThumbnailSourceTooLargeException extends ThumbnailGenerationException {

    public ThumbnailSourceTooLargeException(long maxSourceBytes) {
        super("Thumbnail source exceeds max size " + maxSourceBytes + " bytes");
    }
}
