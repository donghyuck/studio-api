package studio.one.application.avatar.web.dto.request;

public record AvatarImageMetaUpdateRequest(
        String fileName,
        Boolean primaryImage
) {
}
