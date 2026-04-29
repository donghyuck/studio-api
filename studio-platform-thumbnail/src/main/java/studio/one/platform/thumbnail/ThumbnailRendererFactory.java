package studio.one.platform.thumbnail;

import java.util.List;
import java.util.Optional;

public class ThumbnailRendererFactory {

    private final List<ThumbnailRenderer> renderers;

    public ThumbnailRendererFactory(List<ThumbnailRenderer> renderers) {
        this.renderers = List.copyOf(renderers == null ? List.of() : renderers);
    }

    public Optional<ThumbnailRenderer> findRenderer(ThumbnailSource source) {
        return renderers.stream()
                .filter(renderer -> renderer.supports(source))
                .findFirst();
    }

    public List<ThumbnailRenderer> renderers() {
        return renderers;
    }
}
