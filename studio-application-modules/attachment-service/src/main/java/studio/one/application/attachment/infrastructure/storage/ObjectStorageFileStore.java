package studio.one.application.attachment.infrastructure.storage;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageRegistry;

@RequiredArgsConstructor
public class ObjectStorageFileStore implements FileStorage, FileStorageSaveResultCapable {

    private final ObjectStorageRegistry registry;
    private final String providerId;
    private final String bucket;
    private final String keyPrefix;

    @Override
    public String save(Attachment attachment, InputStream input) {
        return saveWithResult(attachment, input).location();
    }

    @Override
    public FileStorageSaveResult saveWithResult(Attachment attachment, InputStream input) {
        if (!StringUtils.hasText(providerId) || !StringUtils.hasText(bucket)) {
            throw new IllegalStateException("Object storage provider and bucket are required for attachment upload");
        }
        String key = newObjectKey();
        storage(providerId).put(bucket, key, input, attachment.getSize(), attachment.getContentType(), Map.of());
        Map<String, String> properties = Map.of(
                AttachmentStorageMetadata.STORAGE_TYPE, AttachmentStorageType.objectstorage.name(),
                AttachmentStorageMetadata.STORAGE_PROVIDER, providerId,
                AttachmentStorageMetadata.STORAGE_BUCKET, bucket,
                AttachmentStorageMetadata.STORAGE_KEY, key);
        return new FileStorageSaveResult(location(providerId, bucket, key), properties);
    }

    @Override
    public InputStream load(Attachment attachment) {
        AttachmentStorageMetadata.ObjectStorageLocation location = location(attachment);
        return storage(location.providerId()).get(location.bucket(), location.key());
    }

    @Override
    public void delete(Attachment attachment) {
        AttachmentStorageMetadata.ObjectStorageLocation location = location(attachment);
        storage(location.providerId()).delete(location.bucket(), location.key());
    }

    private AttachmentStorageMetadata.ObjectStorageLocation location(Attachment attachment) {
        return AttachmentStorageMetadata.explicitObjectStorageLocation(attachment)
                .orElseThrow(() -> new IllegalStateException("Attachment object storage metadata is incomplete"));
    }

    private CloudObjectStorage storage(String id) {
        return registry.get(id);
    }

    private String newObjectKey() {
        return normalizedPrefix() + "/" + UUID.randomUUID();
    }

    private String normalizedPrefix() {
        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "attachments";
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix.isBlank() ? "attachments" : prefix;
    }

    private static String location(String providerId, String bucket, String key) {
        return "objectstorage://" + providerId + "/" + bucket + "/" + key;
    }
}
