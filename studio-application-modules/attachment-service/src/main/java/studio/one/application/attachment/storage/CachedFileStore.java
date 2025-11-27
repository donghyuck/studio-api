package studio.one.application.attachment.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.application.attachment.domain.model.Attachment;

/**
 * Wraps a primary FileStorage (e.g., database) with a filesystem cache to reduce DB reads.
 */
@Slf4j
@RequiredArgsConstructor
public class CachedFileStore implements FileStorage {

    private final FileStorage primary;
    private final FileStorage cache;

    @Override
    public String save(Attachment attachment, InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            String location = primary.save(attachment, new ByteArrayInputStream(bytes));
            try {
                cache.save(attachment, new ByteArrayInputStream(bytes));
            } catch (RuntimeException cacheError) {
                log.warn("Attachment cache write failed for id={}: {}", attachment.getAttachmentId(),
                        cacheError.getMessage());
            }
            return location;
        } catch (IOException e) {
            throw new RuntimeException("Attachment save failed", e);
        }
    }

    @Override
    public InputStream load(Attachment attachment) {
        try {
            return cache.load(attachment);
        } catch (RuntimeException cacheMiss) {
            try (InputStream source = primary.load(attachment)) {
                byte[] bytes = source.readAllBytes();
                try {
                    cache.save(attachment, new ByteArrayInputStream(bytes));
                } catch (RuntimeException cacheError) {
                    log.warn("Attachment cache fill failed for id={}: {}", attachment.getAttachmentId(),
                            cacheError.getMessage());
                }
                return new ByteArrayInputStream(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Attachment load failed", e);
            }
        }
    }

    @Override
    public void delete(Attachment attachment) {
        primary.delete(attachment);
        try {
            cache.delete(attachment);
        } catch (RuntimeException cacheError) {
            log.debug("Attachment cache delete failed for id={}: {}", attachment.getAttachmentId(),
                    cacheError.getMessage());
        }
    }
}
