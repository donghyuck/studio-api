package studio.one.application.attachment.infrastructure.storage;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import studio.one.application.attachment.domain.model.Attachment;

public class AttachmentFileStorageResolver {

    private final AttachmentStorageType defaultStorageType;
    private final FileStorage defaultStorage;
    private final Map<AttachmentStorageType, FileStorage> backends;

    public AttachmentFileStorageResolver(
            AttachmentStorageType defaultStorageType,
            FileStorage defaultStorage,
            Collection<AttachmentStorageBackend> backends) {
        this.defaultStorageType = defaultStorageType;
        this.defaultStorage = defaultStorage;
        this.backends = new EnumMap<>(AttachmentStorageType.class);
        if (backends != null) {
            for (AttachmentStorageBackend backend : backends) {
                if (backend != null && backend.type() != null && backend.storage() != null) {
                    this.backends.put(backend.type(), backend.storage());
                }
            }
        }
    }

    public FileStorage resolveForWrite() {
        if (defaultStorage != null) {
            return defaultStorage;
        }
        return resolve(defaultStorageType)
                .orElseThrow(() -> new IllegalStateException("Attachment storage backend is not configured"));
    }

    public FileStorage resolveForRead(Attachment attachment) {
        Optional<AttachmentStorageType> explicitType = AttachmentStorageMetadata.explicitType(attachment);
        if (explicitType.isEmpty()) {
            return resolveForWrite();
        }
        return resolve(explicitType.get())
                .orElseThrow(() -> new IllegalStateException("Attachment storage backend is not configured"));
    }

    public Optional<FileStorage> resolve(AttachmentStorageType type) {
        if (type == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(backends.get(type));
    }
}
