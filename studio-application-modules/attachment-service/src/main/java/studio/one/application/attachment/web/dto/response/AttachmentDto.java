package studio.one.application.attachment.web.dto.response;

import java.time.Instant;
import java.util.Map;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.identity.UserDto;

public class AttachmentDto {

    private final long attachmentId;
    private final int objectType;
    private final long objectId;
    private final String name;
    private final long size;
    private final String contentType;
    private final Map<String, String> properties;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final UserDto createdBy;

    public AttachmentDto(
            long attachmentId,
            int objectType,
            long objectId,
            String name,
            long size,
            String contentType,
            Map<String, String> properties,
            Instant createdAt,
            Instant updatedAt,
            UserDto createdBy) {
        this.attachmentId = attachmentId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.name = name;
        this.size = size;
        this.contentType = contentType;
        this.properties = properties;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
    }

    public long attachmentId() { return attachmentId; }

    public int objectType() { return objectType; }

    public long objectId() { return objectId; }

    public String name() { return name; }

    public long size() { return size; }

    public String contentType() { return contentType; }

    public Map<String, String> properties() { return properties; }

    public Instant createdAt() { return createdAt; }

    public Instant updatedAt() { return updatedAt; }

    public UserDto createdBy() { return createdBy; }

public static AttachmentDto of(Attachment attachment, UserDto createdBy) {
        return new AttachmentDto(
                attachment.getAttachmentId(),
                attachment.getObjectType(),
                attachment.getObjectId(),
                attachment.getName(),
                attachment.getSize(),
                attachment.getContentType(),
                attachment.getProperties(),
                attachment.getCreatedAt(),
                attachment.getUpdatedAt(),
                createdBy);
    }
    public long getAttachmentId() { return attachmentId; }

    public int getObjectType() { return objectType; }

    public long getObjectId() { return objectId; }

    public String getName() { return name; }

    public long getSize() { return size; }

    public String getContentType() { return contentType; }

    public Map<String, String> getProperties() { return properties; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public UserDto getCreatedBy() { return createdBy; }

}
