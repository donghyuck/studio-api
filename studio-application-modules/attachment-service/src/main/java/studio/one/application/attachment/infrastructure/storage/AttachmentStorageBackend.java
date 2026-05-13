package studio.one.application.attachment.infrastructure.storage;

public class AttachmentStorageBackend {

    private final AttachmentStorageType type;
    private final FileStorage storage;

    public AttachmentStorageBackend(
            AttachmentStorageType type,
            FileStorage storage) {
        this.type = type;
        this.storage = storage;
    }

    public AttachmentStorageType type() { return type; }

    public FileStorage storage() { return storage; }

}
