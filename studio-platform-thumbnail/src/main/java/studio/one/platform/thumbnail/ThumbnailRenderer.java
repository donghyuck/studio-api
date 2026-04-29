package studio.one.platform.thumbnail;

public interface ThumbnailRenderer {

    boolean supports(ThumbnailSource source);

    ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) throws ThumbnailGenerationException;
}
