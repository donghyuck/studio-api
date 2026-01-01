package studio.one.application.web.dto;

public record AvatarImageMetaUpdateRequest(
        String fileName,
        Boolean primaryImage
) {
}
