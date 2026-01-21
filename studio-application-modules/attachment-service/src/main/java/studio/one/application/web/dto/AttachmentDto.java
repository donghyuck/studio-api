package studio.one.application.web.dto;

import java.time.Instant;
import java.util.Map;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.identity.UserDto;

public record AttachmentDto(
    
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
}
