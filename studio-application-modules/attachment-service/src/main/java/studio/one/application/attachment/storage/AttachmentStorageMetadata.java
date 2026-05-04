package studio.one.application.attachment.storage;

import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

import studio.one.application.attachment.domain.model.Attachment;

public final class AttachmentStorageMetadata {

    public static final String STORAGE_TYPE = "storage.type";
    public static final String STORAGE_PROVIDER = "storage.provider";
    public static final String STORAGE_BUCKET = "storage.bucket";
    public static final String STORAGE_KEY = "storage.key";

    private AttachmentStorageMetadata() {
    }

    public static Optional<AttachmentStorageType> explicitType(Attachment attachment) {
        return AttachmentStorageType.from(value(attachment, STORAGE_TYPE));
    }

    public static Optional<ObjectStorageLocation> explicitObjectStorageLocation(Attachment attachment) {
        Optional<AttachmentStorageType> type = explicitType(attachment);
        if (type.isEmpty() || type.get() != AttachmentStorageType.objectstorage) {
            return Optional.empty();
        }
        String provider = value(attachment, STORAGE_PROVIDER);
        String bucket = value(attachment, STORAGE_BUCKET);
        String key = value(attachment, STORAGE_KEY);
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(bucket) || !StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return Optional.of(new ObjectStorageLocation(provider, bucket, key));
    }

    public static String value(Attachment attachment, String key) {
        if (attachment == null) {
            return null;
        }
        Map<String, String> properties = attachment.getProperties();
        if (properties == null) {
            return null;
        }
        return properties.get(key);
    }

    public record ObjectStorageLocation(String providerId, String bucket, String key) {
    }
}
