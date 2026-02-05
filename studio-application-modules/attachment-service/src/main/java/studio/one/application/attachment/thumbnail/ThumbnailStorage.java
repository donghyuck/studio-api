package studio.one.application.attachment.thumbnail;

import java.io.InputStream;

public interface ThumbnailStorage {
    
    String save(ThumbnailKey key, InputStream input);

    InputStream load(ThumbnailKey key);

    void delete(ThumbnailKey key);

    void deleteAll(int objectType, long attachmentId);
}
