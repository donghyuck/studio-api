package studio.one.application.attachment.thumbnail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;

@RequiredArgsConstructor
@Slf4j
public class ThumbnailServiceImpl implements ThumbnailService {

    private final AttachmentService attachmentService;
    private final ThumbnailStorage thumbnailStorage;
    private final ThumbnailGenerationService thumbnailGenerationService;

    /**
     * @deprecated Use {@link #ThumbnailServiceImpl(AttachmentService, ThumbnailStorage,
     *             ThumbnailGenerationService)} so thumbnail generation settings come from
     *             the platform thumbnail service.
     */
    @Deprecated(since = "2.x", forRemoval = false)
    public ThumbnailServiceImpl(
            AttachmentService attachmentService,
            ThumbnailStorage thumbnailStorage,
            int defaultSize,
            String defaultFormat) {
        this(attachmentService, thumbnailStorage, legacyGenerationService(defaultSize, defaultFormat));
    }

    @Override
    public Optional<ThumbnailData> getOrCreate(Attachment attachment, int size, String format) {
        if (attachment == null) {
            return Optional.empty();
        }
        ThumbnailOptions options = thumbnailGenerationService.resolveOptions(size, format);
        ThumbnailKey key = new ThumbnailKey(
                attachment.getObjectType(),
                attachment.getAttachmentId(),
                options.size(),
                options.format());
        try (InputStream cached = thumbnailStorage.load(key)) {
            byte[] bytes = cached.readAllBytes();
            return Optional.of(new ThumbnailData(bytes, contentTypeFor(options.format())));
        } catch (RuntimeException | java.io.IOException ignored) {
            // cache miss or load error -> generate
        }

        try (InputStream source = attachmentService.getInputStream(attachment)) {
            Optional<ThumbnailResult> result = thumbnailGenerationService.generate(
                    attachment.getContentType(),
                    attachment.getName(),
                    source,
                    options.size(),
                    options.format());
            if (result.isEmpty()) {
                return Optional.empty();
            }
            ThumbnailResult thumbnail = result.get();
            thumbnailStorage.save(key, new ByteArrayInputStream(thumbnail.bytes()));
            return Optional.of(new ThumbnailData(thumbnail.bytes(), thumbnail.contentType()));
        } catch (Exception e) {
            log.warn("Thumbnail generate failed for id={}: {}", attachment.getAttachmentId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void deleteAll(Attachment attachment) {
        if (attachment == null) {
            return;
        }
        try {
            thumbnailStorage.deleteAll(attachment.getObjectType(), attachment.getAttachmentId());
        } catch (RuntimeException ex) {
            log.debug("Thumbnail deleteAll failed for id={}: {}", attachment.getAttachmentId(), ex.getMessage());
        }
    }

    private String contentTypeFor(String format) {
        if ("png".equalsIgnoreCase(format)) {
            return "image/png";
        }
        return "image/png";
    }

    private static ThumbnailGenerationService legacyGenerationService(int defaultSize, String defaultFormat) {
        return new ThumbnailGenerationService(
                new ThumbnailRendererFactory(List.of(new ImageThumbnailRenderer())),
                new ThumbnailGenerationOptions(defaultSize, defaultFormat, 16, 512, 50L * 1024L * 1024L, 25_000_000L));
    }
}
