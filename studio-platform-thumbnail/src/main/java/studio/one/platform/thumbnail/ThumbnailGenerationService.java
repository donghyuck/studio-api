package studio.one.platform.thumbnail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class ThumbnailGenerationService {

    private static final int BUFFER_SIZE = 8192;

    private final ThumbnailRendererFactory rendererFactory;
    private final ThumbnailGenerationOptions generationOptions;

    public ThumbnailGenerationService(
            ThumbnailRendererFactory rendererFactory,
            ThumbnailGenerationOptions generationOptions) {
        this.rendererFactory = rendererFactory;
        this.generationOptions = generationOptions;
    }

    public ThumbnailGenerationOptions generationOptions() {
        return generationOptions;
    }

    public ThumbnailOptions resolveOptions(int size, String format) {
        int resolvedSize = size > 0 ? size : generationOptions.defaultSize();
        resolvedSize = Math.max(generationOptions.minSize(), Math.min(generationOptions.maxSize(), resolvedSize));
        String resolvedFormat = ThumbnailFormats.normalizeOrDefault(format, generationOptions.defaultFormat());
        return new ThumbnailOptions(resolvedSize, resolvedFormat, generationOptions.maxSourcePixels());
    }

    public Optional<ThumbnailResult> generate(
            String contentType,
            String filename,
            InputStream input,
            int size,
            String format) {
        if (input == null) {
            return Optional.empty();
        }
        byte[] bytes = readBounded(input);
        return generate(new ThumbnailSource(contentType, filename, bytes), size, format);
    }

    public Optional<ThumbnailResult> generate(ThumbnailSource source, int size, String format) {
        if (source == null || source.size() == 0) {
            return Optional.empty();
        }
        if (source.size() > generationOptions.maxSourceBytes()) {
            throw new ThumbnailSourceTooLargeException(generationOptions.maxSourceBytes());
        }
        ThumbnailOptions options = resolveOptions(size, format);
        return rendererFactory.findRenderer(source)
                .map(renderer -> renderer.render(source, options));
    }

    private byte[] readBounded(InputStream input) {
        long max = generationOptions.maxSourceBytes();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new ThumbnailSourceTooLargeException(max);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to read thumbnail source", ex);
        }
    }
}
