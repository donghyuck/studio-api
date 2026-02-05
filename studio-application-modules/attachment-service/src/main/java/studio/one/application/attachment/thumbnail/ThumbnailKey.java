package studio.one.application.attachment.thumbnail;

import java.util.Objects;

public final class ThumbnailKey {
    private final int objectType;
    private final long attachmentId;
    private final int size;
    private final String format;

    public ThumbnailKey(int objectType, long attachmentId, int size, String format) {
        this.objectType = objectType;
        this.attachmentId = attachmentId;
        this.size = size;
        this.format = format;
    }

    public int getObjectType() {
        return objectType;
    }

    public long getAttachmentId() {
        return attachmentId;
    }

    public int getSize() {
        return size;
    }

    public String getFormat() {
        return format;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThumbnailKey that)) {
            return false;
        }
        return objectType == that.objectType
                && attachmentId == that.attachmentId
                && size == that.size
                && Objects.equals(format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectType, attachmentId, size, format);
    }
}
