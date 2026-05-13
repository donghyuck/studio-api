package studio.one.application.avatar.web.dto.response;

import java.time.OffsetDateTime;

import studio.one.application.avatar.domain.model.AvatarImage;

// DTO
public class AvatarImageDto {

    private final Long id;
    private final Long userId;
    private final boolean primaryImage;
    private final String fileName;
    private final Long fileSize;
    private final String contentType;
    private final OffsetDateTime creationDate;
    private final OffsetDateTime modifiedDate;

    public AvatarImageDto(
            Long id,
            Long userId,
            boolean primaryImage,
            String fileName,
            Long fileSize,
            String contentType,
            OffsetDateTime creationDate,
            OffsetDateTime modifiedDate) {
        this.id = id;
        this.userId = userId;
        this.primaryImage = primaryImage;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.creationDate = creationDate;
        this.modifiedDate = modifiedDate;
    }

    public Long getId() { return id; }

    public Long id() { return id; }

    public Long getUserId() { return userId; }

    public Long userId() { return userId; }

    public boolean isPrimaryImage() { return primaryImage; }

    public boolean primaryImage() { return primaryImage; }

    public String getFileName() { return fileName; }

    public String fileName() { return fileName; }

    public Long getFileSize() { return fileSize; }

    public Long fileSize() { return fileSize; }

    public String getContentType() { return contentType; }

    public String contentType() { return contentType; }

    public OffsetDateTime getCreationDate() { return creationDate; }

    public OffsetDateTime creationDate() { return creationDate; }

    public OffsetDateTime getModifiedDate() { return modifiedDate; }

    public OffsetDateTime modifiedDate() { return modifiedDate; }

public static AvatarImageDto of(AvatarImage e) {
        return new AvatarImageDto(
            e.getId(), e.getUserId(), e.isPrimaryImage(),
            e.getFileName(), e.getFileSize(), e.getContentType(),
            e.getCreationDate(), e.getModifiedDate()
        );
    }
}
