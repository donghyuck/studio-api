package studio.one.application.web.dto;

import java.time.OffsetDateTime;

import studio.one.application.avatar.domain.entity.AvatarImage;

// DTO
public record AvatarImageDto(
    Long id, Long userId, boolean primaryImage,
    String fileName, Long fileSize, String contentType,
    OffsetDateTime creationDate, OffsetDateTime modifiedDate
) {
    public static AvatarImageDto of(AvatarImage e) {
        return new AvatarImageDto(
            e.getId(), e.getUserId(), e.isPrimaryImage(),
            e.getFileName(), e.getFileSize(), e.getContentType(),
            e.getCreationDate(), e.getModifiedDate()
        );
    }
}
