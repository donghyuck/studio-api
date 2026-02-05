package studio.one.application.attachment.thumbnail;

import java.util.Optional;

import studio.one.application.attachment.domain.model.Attachment;

public interface ThumbnailService {
    Optional<ThumbnailData> getOrCreate(Attachment attachment, int size, String format);

    void deleteAll(Attachment attachment);
}
