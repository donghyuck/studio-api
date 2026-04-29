package studio.one.application.attachment.thumbnail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.thumbnail.ThumbnailGenerationOptions;
import studio.one.platform.thumbnail.ThumbnailGenerationService;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRendererFactory;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.renderer.ImageThumbnailRenderer;

@Slf4j
public class ThumbnailServiceImpl implements ThumbnailService {

    private final AttachmentService attachmentService;
    private final ThumbnailStorage thumbnailStorage;
    private final ThumbnailGenerationService thumbnailGenerationService;
    private final FailureMemo failedThumbnails = new FailureMemo(10_000, Duration.ofMinutes(10));
    private final DeletionMemo deletedThumbnails = new DeletionMemo(10_000, Duration.ofMinutes(10));
    private final Set<ThumbnailSourceKey> runningThumbnails = ConcurrentHashMap.newKeySet();
    private final Object[] generationLocks = createLocks(64);
    private final Executor generationExecutor;

    public ThumbnailServiceImpl(
            AttachmentService attachmentService,
            ThumbnailStorage thumbnailStorage,
            ThumbnailGenerationService thumbnailGenerationService) {
        this(attachmentService, thumbnailStorage, thumbnailGenerationService, Runnable::run);
    }

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
        this(attachmentService, thumbnailStorage, legacyGenerationService(defaultSize, defaultFormat), Runnable::run);
    }

    public ThumbnailServiceImpl(
            AttachmentService attachmentService,
            ThumbnailStorage thumbnailStorage,
            ThumbnailGenerationService thumbnailGenerationService,
            Executor generationExecutor) {
        this.attachmentService = attachmentService;
        this.thumbnailStorage = thumbnailStorage;
        this.thumbnailGenerationService = thumbnailGenerationService;
        this.generationExecutor = generationExecutor == null ? Runnable::run : generationExecutor;
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
        ThumbnailSourceKey sourceKey = ThumbnailSourceKey.from(attachment, options.format());
        try (InputStream cached = thumbnailStorage.load(key)) {
            byte[] bytes = cached.readAllBytes();
            return Optional.of(new ThumbnailData(bytes, contentTypeFor(options.format())));
        } catch (RuntimeException | java.io.IOException ignored) {
            // cache miss or load error -> generate
        }
        if (failedThumbnails.contains(sourceKey)) {
            return Optional.empty();
        }
        if (!enqueueGeneration(attachment, key, sourceKey, options)) {
            return Optional.empty();
        }
        return Optional.of(ThumbnailPlaceholder.pending(options.size()));
    }

    private boolean enqueueGeneration(
            Attachment attachment,
            ThumbnailKey key,
            ThumbnailSourceKey sourceKey,
            ThumbnailOptions options) {
        if (!runningThumbnails.add(sourceKey)) {
            return true;
        }
        try {
            generationExecutor.execute(() -> generateAndStore(attachment, key, sourceKey, options));
            return true;
        } catch (RejectedExecutionException ex) {
            runningThumbnails.remove(sourceKey);
            log.warn("Thumbnail generation queue rejected id={}: {}",
                    attachment.getAttachmentId(), ex.getMessage());
            return false;
        }
    }

    private void generateAndStore(
            Attachment attachment,
            ThumbnailKey key,
            ThumbnailSourceKey sourceKey,
            ThumbnailOptions options) {
        try (InputStream source = attachmentService.getInputStream(attachment)) {
            Optional<ThumbnailResult> result = thumbnailGenerationService.generate(
                    attachment.getContentType(),
                    attachment.getName(),
                    source,
                    options.size(),
                    options.format());
            if (result.isEmpty()) {
                failedThumbnails.add(sourceKey);
                return;
            }
            ThumbnailResult thumbnail = result.get();
            AttachmentIdentity identity = AttachmentIdentity.from(attachment);
            synchronized (lockFor(identity)) {
                if (deletedThumbnails.contains(identity)) {
                    return;
                }
                thumbnailStorage.save(key, new ByteArrayInputStream(thumbnail.bytes()));
            }
            failedThumbnails.remove(sourceKey);
        } catch (ThumbnailGenerationException e) {
            failedThumbnails.add(sourceKey);
            log.warn("Thumbnail generate failed for id={}: {}", attachment.getAttachmentId(), e.getMessage());
        } catch (Exception e) {
            failedThumbnails.add(sourceKey);
            log.warn("Thumbnail generate failed for id={}: {}", attachment.getAttachmentId(), e.getMessage());
        } finally {
            runningThumbnails.remove(sourceKey);
        }
    }

    @Override
    public void deleteAll(Attachment attachment) {
        if (attachment == null) {
            return;
        }
        try {
            AttachmentIdentity identity = AttachmentIdentity.from(attachment);
            synchronized (lockFor(identity)) {
                thumbnailStorage.deleteAll(attachment.getObjectType(), attachment.getAttachmentId());
                deletedThumbnails.add(identity);
                failedThumbnails.removeAttachment(attachment.getObjectType(), attachment.getAttachmentId());
                runningThumbnails.removeIf(key -> key.objectType() == attachment.getObjectType()
                        && key.attachmentId() == attachment.getAttachmentId());
            }
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

    private Object lockFor(AttachmentIdentity identity) {
        int index = Math.floorMod(identity.hashCode(), generationLocks.length);
        return generationLocks[index];
    }

    private static Object[] createLocks(int count) {
        Object[] locks = new Object[count];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private record AttachmentIdentity(int objectType, long attachmentId) {

        private static AttachmentIdentity from(Attachment attachment) {
            return new AttachmentIdentity(attachment.getObjectType(), attachment.getAttachmentId());
        }
    }

    private record ThumbnailSourceKey(int objectType, long attachmentId, String format) {

        private static ThumbnailSourceKey from(Attachment attachment, String format) {
            return new ThumbnailSourceKey(
                    attachment.getObjectType(),
                    attachment.getAttachmentId(),
                    format == null ? "" : format.toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final class DeletionMemo {

        private final int maxEntries;
        private final long ttlMillis;
        private final LinkedHashMap<AttachmentIdentity, Long> entries = new LinkedHashMap<>(16, 0.75f, true);

        private DeletionMemo(int maxEntries, Duration ttl) {
            this.maxEntries = maxEntries;
            this.ttlMillis = ttl.toMillis();
        }

        private synchronized boolean contains(AttachmentIdentity key) {
            long now = Instant.now().toEpochMilli();
            Long deletedAt = entries.get(key);
            if (deletedAt == null) {
                return false;
            }
            if (now - deletedAt > ttlMillis) {
                entries.remove(key);
                return false;
            }
            return true;
        }

        private synchronized void add(AttachmentIdentity key) {
            entries.put(key, Instant.now().toEpochMilli());
            trim();
        }

        private void trim() {
            Iterator<Map.Entry<AttachmentIdentity, Long>> iterator = entries.entrySet().iterator();
            while (entries.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static final class FailureMemo {

        private final int maxEntries;
        private final long ttlMillis;
        private final LinkedHashMap<ThumbnailSourceKey, Long> entries = new LinkedHashMap<>(16, 0.75f, true);

        private FailureMemo(int maxEntries, Duration ttl) {
            this.maxEntries = maxEntries;
            this.ttlMillis = ttl.toMillis();
        }

        private synchronized boolean contains(ThumbnailSourceKey key) {
            long now = Instant.now().toEpochMilli();
            Long failedAt = entries.get(key);
            if (failedAt == null) {
                return false;
            }
            if (now - failedAt > ttlMillis) {
                entries.remove(key);
                return false;
            }
            return true;
        }

        private synchronized void add(ThumbnailSourceKey key) {
            entries.put(key, Instant.now().toEpochMilli());
            trim();
        }

        private synchronized void remove(ThumbnailSourceKey key) {
            entries.remove(key);
        }

        private synchronized void removeAttachment(int objectType, long attachmentId) {
            entries.keySet().removeIf(key -> key.objectType() == objectType && key.attachmentId() == attachmentId);
        }

        private void trim() {
            Iterator<Map.Entry<ThumbnailSourceKey, Long>> iterator = entries.entrySet().iterator();
            while (entries.size() > maxEntries && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}
