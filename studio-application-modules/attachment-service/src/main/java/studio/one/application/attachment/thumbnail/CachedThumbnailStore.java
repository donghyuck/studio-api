package studio.one.application.attachment.thumbnail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CachedThumbnailStore implements ThumbnailStorage {

    private final ThumbnailStorage primary;
    private final ThumbnailStorage cache;

    @Override
    public String save(ThumbnailKey key, InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            String location = primary.save(key, new ByteArrayInputStream(bytes));
            try {
                cache.save(key, new ByteArrayInputStream(bytes));
            } catch (RuntimeException cacheError) {
                log.warn("Thumbnail cache write failed for id={}: {}", key.getAttachmentId(),
                        cacheError.getMessage());
            }
            return location;
        } catch (IOException e) {
            throw new RuntimeException("Thumbnail save failed", e);
        }
    }

    @Override
    public InputStream load(ThumbnailKey key) {
        try {
            return cache.load(key);
        } catch (RuntimeException cacheMiss) {
            try (InputStream source = primary.load(key)) {
                byte[] bytes = source.readAllBytes();
                try {
                    cache.save(key, new ByteArrayInputStream(bytes));
                } catch (RuntimeException cacheError) {
                    log.warn("Thumbnail cache fill failed for id={}: {}", key.getAttachmentId(),
                            cacheError.getMessage());
                }
                return new ByteArrayInputStream(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Thumbnail load failed", e);
            }
        }
    }

    @Override
    public void delete(ThumbnailKey key) {
        primary.delete(key);
        try {
            cache.delete(key);
        } catch (RuntimeException cacheError) {
            log.debug("Thumbnail cache delete failed for id={}: {}", key.getAttachmentId(),
                    cacheError.getMessage());
        }
    }

    @Override
    public void deleteAll(int objectType, long attachmentId) {
        primary.deleteAll(objectType, attachmentId);
        try {
            cache.deleteAll(objectType, attachmentId);
        } catch (RuntimeException cacheError) {
            log.debug("Thumbnail cache deleteAll failed for id={}: {}", attachmentId,
                    cacheError.getMessage());
        }
    }
}
